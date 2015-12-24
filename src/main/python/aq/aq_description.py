class Fact:
    def __init__(self, attr_id, values, attr_name=''):
        self.attr_id = attr_id
        self.values = values
        self.attr_name = attr_name

    def __str__(self):
        return '{0}={1}'.format(self.attr_name, self.values)

    def __repr__(self):
        return 'attr_{0}={1}'.format(self.attr_id, self.values)


class Rule:
    def __init__(self, rid, facts, covered_positives=0, covered_negatives=0,
                 complexity=0, cost=0, significance=0):
        self.rid = rid
        if facts:
            self.facts = facts
        else:
            self.facts = []

        self.covered_positives = covered_positives
        self.covered_negatives = covered_negatives
        self.complexity = complexity
        self.cost = cost
        self.significance = significance

    def __str__(self):
        return 'Rule {0} (p={1},n={2},cx={3},c={4},s={5}):\n\t'.format(self.rid,
                                                                       self.covered_positives, self.covered_negatives,
                                                                       self.complexity, self.cost,
                                                                       self.significance) + '\n\t'.join(
            [str(fact) for fact in self.facts])

    def __repr__(self):
        return '(' + ' '.join(repr(fact) for fact in self.facts) + ')'


class ClassDescription:
    def __init__(self, class_name, rules):
        self.class_name = class_name

        if rules:
            self.rules = rules
        else:
            self.rules = []

    def __str__(self):
        s = sum([len(r.facts) for r in self.rules])
        return 'Description of class {0} ({1} rules, {2} facts):\n'.format(self.class_name, len(self.rules),
                                                                           s) + '\n'.join([str(r) for r in self.rules])

    def __repr__(self):
        return '[{0}]'.format(' '.join([repr(r) for r in self.rules]))