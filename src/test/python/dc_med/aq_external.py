__author__ = 'Aleksandr Panov'

import subprocess
import sys
import pandas as pd
import numpy as np

class_column = 'attr_20'
saved_column_names = []
saved_category_names = {}


def load_data(input_name, goal_column):
    # load data and rename columns
    data = pd.read_csv(input_name, encoding='utf-8')
    data = data.drop(data.columns[0], axis=1)
    col_len = data.shape[1]
    saved_column_names.extend(list(data.columns))
    data.columns = ['attr_' + str(x) for x in range(col_len)]

    # replace '-' to NaN and rename categories to floats

    single_cat_columns = []

    for x in data.columns:
        column = data[x]
        if column.dtype == 'O':
            column = column.astype('category')

            if '-' in column.cat.categories:
                column[column == '-'] = np.nan
                column = column.cat.remove_categories('-')

            if len(column.cat.categories) == 1:
                single_cat_columns.append(x)
            else:
                saved_category_names[x] = list(column.cat.categories)
                data[x] = column.cat.rename_categories([str(x) for x in range(len(column.cat.categories))])

    if single_cat_columns:
        data = data.drop(single_cat_columns, axis=1)

    data = data.dropna(subset=[goal_column])
    data = data.dropna(axis=1, how='all')

    return data


def _generate_attrs(data):
    result = ''
    for column in data.columns:
        result += data[column].name
        if data[column].dtype.name == 'category':
            result += ' nominal {' + ', '.join(data[column].cat.categories) + '}'
        else:
            result += ' continuous ChiMerge 3'
        result += '\n'

    return result


def _generate_runs(data):
    result = ''
    for clazz in data[class_column].cat.categories:
        result += """
    rules_for_{1}
    {{
        Ambiguity = IgnoreForLearning
        Consequent = [{0}={1}]
        Display_selectors_coverage = false
        Display_events_covered = true
        Maxrule = 5
        Maxstar = 2
        Mode = ATF
    }}
        """.format(class_column, clazz)

    return result


def _generate_events(data):
#    columns = list(data.columns)
#    columns.remove(class_column)
    result = data.to_csv(None, sep=',', na_rep='?', index=False, header=False)
    return result


def generate_input(data):
    text = """
Problem_description
{
	Building classRules for classes
}
Attributes
{
"""
    text += _generate_attrs(data)
    text += """
}

Runs
{
	Attribute_selection_method = promise
"""
    text += _generate_runs(data)
    text += """
}
Events
{
"""
    text += _generate_events(data)
    text += """
}
"""
    return text


if __name__ == "__main__":
    loaded = load_data('real_sample_2.csv', class_column)

    input_text = generate_input(loaded)

    file_name = 'input.aq21'
    f = open(file_name, 'w')
    f.write(input_text)
    f.flush()

    ex_name = 'aq21' if sys.platform == 'linux' else 'aq21.exe'
    output = subprocess.Popen(['aq21.exe', file_name], stdout=subprocess.PIPE).communicate()[0]

    print(output)
