import data.data_loading as dl
import aq.aq_external as aq
from jsm.jsm_analysis import FactBase, search_norris
import sys
import argparse
import logging

log_levels = ['debug', 'info', 'warning', 'error']

if __name__ == "__main__":
    argparser = argparse.ArgumentParser(formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    argparser.add_argument(dest='datafile')
    argparser.add_argument('-l', '--loglevel', choices=log_levels, default='info')
    argparser.add_argument('-s', '--reasonsize', default='3')
    argparser.add_argument('-u', '--univer', default='30')
    args = argparser.parse_args()

    logging.basicConfig(level=getattr(logging, args.loglevel.upper()),
                        format='%(asctime)s %(levelname)-8s %(message)s',
                        stream=sys.stdout)

    logging.info(args)
    max_universe_size = int(args.univer)
    max_reason_length = int(args.reasonsize)

    data, class_column = dl.load_data(args.datafile)

    logging.info(
        'Data file {0}: {2} columns, {3} objects, class column is {1}'.format(args.datafile, class_column,
                                                                              *reversed(data.shape)))

    class_descriptions = aq.run_aq(data, class_column, dl.column_names)
    for desc in class_descriptions.values():
        desc.build(max_universe_size)

    logging.info('\n'.join([str(class_descriptions[d]) for d in class_descriptions]))

    for klass in data[class_column].unique():
        def _search_in_fb(data_fb, target):
            hypotheses = search_norris(data_fb)
            reasons = []
            for hyp in hypotheses:
                if hyp.value.count() <= max_reason_length:
                    reasons.append(
                        [class_descriptions[klass].properties[i] for i in range(len(hyp.value)) if hyp.value[i]])
            if reasons:
                logging.info('Found {0} reasons for {1}:\n'.format(len(reasons), target) + '\n\t'.join(reasons))
            else:
                logging.info('Was not found reasons for {0}'.format(target))


        logging.info('*' * 5 + 'Start search reasons for class {0}'.format(klass) + '*' * 5)
        fb = FactBase(class_column, [klass])
        fb.build(data, class_descriptions[klass])
        fb.clear()

        _search_in_fb(fb, 'class ' + klass)

        for prop in class_descriptions[klass].properties:
            fb = FactBase(prop.canon_attr_name, prop.values)
            fb.build(data, class_descriptions[klass])
            fb.clear()

            _search_in_fb(fb, prop)
