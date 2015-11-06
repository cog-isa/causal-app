__author__ = 'Aleksandr Panov'

import numpy as np
import pandas as pd

saved_column_names = []
saved_category_names = {}


def load_data(input_name, goal_column):
    # load data and rename columns
    data = pd.read_csv(input_name, encoding='utf-8')
    data = data.drop(data.columns[0], axis=1)
    col_len = data.shape[1]
    saved_column_names.extend(list(data.columns))
    data.columns = [x for x in range(col_len)]

    for i in range(len(data[21].values)):
        value = data[19][i]
        if value == 'Months':
            data.loc[i, 21] *= 30
        elif value == 'Weeks':
            data.loc[i, 21] *= 7
        elif value == 'Years':
            data.loc[i, 21] *= 365

    data = data.drop(19, axis=1)

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
    data = data.dropna(thresh=int(0.05 * data.shape[1]))
    data = data.dropna(axis=1, thresh=int(0.05 * data.shape[0]))

    return data


if __name__ == "__main__":
    input_file = 'real_sample_2.csv'
    output_file = 'real_class_20.gqj'
    target_column = 20  # 34

    data = load_data(input_file, target_column)

    f = open(output_file, 'w')
    # in weka indexes of nominal columns (from 1)
    f.write(str(data.columns.get_loc(target_column)) + '\n' +
            ','.join([str(data.columns.get_loc(x) + 1) for x in saved_category_names if x in data.columns]) + '\n')
    # in weka values of nominal columns (index from 1)
    f.write(';'.join([str(str(data.columns.get_loc(x) + 1)) + ':' +
                      ','.join(list(data[x].cat.categories)) for x in saved_category_names if
                      x in data.columns]) + '\n')
    f.flush()

    data.to_csv(output_file, sep='\t', na_rep='?', index=False, mode='a')
