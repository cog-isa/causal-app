import data.data_loading as dl
import aq.aq_external as aq
import sys

if __name__ == "__main__":
    data, class_column = dl.load_data(sys.argv[1])

    print(data.shape)

    class_descriptions = aq.run_aq(data, class_column, dl.column_names)

    print('\n'.join([str(class_descriptions[d]) for d in class_descriptions]))
