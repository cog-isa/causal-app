import itertools
import math
from bitarray import bitarray
import logging


class FactBase:
    def __init__(self, class_column, class_value):
        self.class_column = class_column
        self.class_value = class_value
        self.positives = {}
        self.negatives = {}
        self.num_events = 0
        self.num_props = 0

    def __str__(self):
        return 'FactBase for class {0} ({1} props, {2} events: p={3}, n={4}):\n\t'.format(self.class_value,
                                                                                          self.num_props,
                                                                                          self.num_events,
                                                                                          len(self.positives), len(
                self.negatives)) + '\n\t'.join(
            [b.to01() for b in itertools.chain(self.positives.values(), self.negatives.values())])

    def __repr__(self):
        return 'FactBase for class {0} (p={1}, n={2}):\n\t'.format(self.class_value, len(self.positives),
                                                                   len(self.negatives))

    def build(self, data, class_descriptions, max_universe_size):
        class_index = data.columns.get_loc(self.class_column)
        class_desc_size = len(class_descriptions[self.class_value].properties)
        self.num_props = class_desc_size if max_universe_size > class_desc_size else max_universe_size
        self.num_events = len(data.values)
        counter = 0
        for i, row in enumerate(data.values):
            b = bitarray(self.num_props)
            for j in range(self.num_props):
                prop = class_descriptions[self.class_value].properties[j]
                value = row[prop.attr_id]
                b[j] = False if math.isnan(float(value)) else int(value) in prop.values
            if row[class_index] == self.class_value and b not in self.positives.values():
                self.positives[i] = b
            elif b not in self.negatives.values():
                self.negatives[i] = b
            else:
                counter += 1
        logging.info('Delete {0} duplicated events'.format(counter))

    def clear(self):
        counter = 0
        for key in list(self.negatives.keys()):
            if self.negatives[key] in self.positives.values():
                del self.negatives[key]
                counter += 1
        logging.info('Delete {0} conflicted events'.format(counter))


class JSMHypothesis:
    def __init__(self, value, generator=None):
        self.value = value
        if generator:
            self.generator = generator
        else:
            self.generator = set()

    def __str__(self):
        return 'Hypothesis {0} by {1}'.format(self.value.to01(), self.generator)

    def __repr__(self):
        return self.value.to01()

    def __eq__(self, other):
        return self.value == other.value and self.generator == other.generator

    def __hash__(self):
        return 3 * hash(self.value.to01()) + 5 * hash(str(self.generator))


def search_norris(fb):
    pos_inters = _search_norris(fb.positives)
    neg_inters = _search_norris(fb.negatives)

    sparse = []
    for i, p_inter in enumerate(pos_inters):
        for n_inter in neg_inters:
            unit = p_inter.value | n_inter.value
            if len(p_inter.generator) < 2 or unit == p_inter.value or unit == n_inter.value:
                sparse.append(i)

    pos_inters = [pos_inters[i] for i in range(len(pos_inters)) if i not in sparse]
    return pos_inters


def _search_norris(positives):
    # Relation R=AxB, A - objects, B - features, Mk - maximal rectangles (maximal intersections)
    hypotheses = []
    for key, value in positives.items():
        # compute collection Tk={Ax(B intersect xkR): AxB in Mk-1}
        tmp_hyps = [JSMHypothesis(value & h.value, h.generator) for h in hypotheses if (value & h.value).any()]
        # eliminate the members of Tk which are proper subsets of other members of Tk;
        # remaining sets are the members of T'k
        spares = set()
        for i in range(len(tmp_hyps)):
            for j in range(len(tmp_hyps)):
                if not i == j and (tmp_hyps[i].value | tmp_hyps[j].value) == tmp_hyps[j].value and tmp_hyps[
                    j].generator >= tmp_hyps[i].generator:
                    spares.add(i)
        tmp_hyps = [tmp_hyps[i] for i in range(len(tmp_hyps)) if i not in spares]

        # for each CxD in    Mk-1
        new_hyps = []
        add_example = True
        for hyp in hypotheses:
            # if D subsetoreq xkR then (C unite xk)xD in Mk
            if (hyp.value | value) == value:
                hyp.generator.add(key)
            else:
                # if D not susetoreq xkR then CxD in Mk, and (C unite xk)x(D intersect xkR) in Mk
                # if and only if emptyset noteq Cx(D intersect xkR) in T'k
                new_hyp = JSMHypothesis(hyp.value & value, hyp.generator)
                if new_hyp.value.any() and new_hyp in tmp_hyps:
                    new_hyps.append(new_hyp)
            if not value.any() or (hyp.value | value) == hyp.value:
                add_example = False

        hypotheses.extend(new_hyps)
        if add_example:
            hypotheses.append(JSMHypothesis(value, {key}))
    return hypotheses


if __name__ == '__main__':
    fb = FactBase(0, '1')
    fb.positives = {1: bitarray('11000'), 2: bitarray('11010'), 3: bitarray('11100')}
    fb.negatives = {4: bitarray('00101'), 5: bitarray('00110'), 6: bitarray('00011')}

    hypotheses = search_norris(fb)
    print('\n'.join(map(str, hypotheses)))
