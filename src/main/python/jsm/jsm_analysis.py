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
