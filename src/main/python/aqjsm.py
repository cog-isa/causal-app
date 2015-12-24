import data.data_loading as dl
import aq.aq_external as aq
import sys
import argparse
import logging

log_levels = ['debug', 'info', 'warning', 'error']

if __name__ == "__main__":
    argparser = argparse.ArgumentParser(formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    argparser.add_argument(dest='datafile')
    argparser.add_argument('-l', '--loglevel', choices=log_levels, default='info')
    args = argparser.parse_args()

    logging.basicConfig(level=getattr(logging, args.loglevel.upper()),
                        format='%(asctime)s %(levelname)-8s %(message)s',
                        stream=sys.stdout)

    data, class_column = dl.load_data(args.datafile)

    class_descriptions = aq.run_aq(data, class_column, dl.column_names)
    for desc in class_descriptions.values():
        desc.build()

    logging.info('\n'.join([str(class_descriptions[d]) for d in class_descriptions]))
