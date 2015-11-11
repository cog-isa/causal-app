__author__ = 'Aleksandr Panov'

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

saved_column_names = []
saved_category_names = {}
saved_bins = {}

# load data and rename columns
data = pd.read_csv('real_sample_2.csv', encoding='utf-8')
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

row_to_drop = []
for i in range(len(data[34].values)):
    y = data[34][i]
    if y == 'Complete response' or y == 'Partial Response':
        data.loc[i, 34] = 'Success'
    elif y == 'Progressive Disease' or y == 'Stable Disease' or y =='Stable Desease' or y == 'Death':
        data.loc[i, 34] = 'Fail'
    elif y == 'Not applicable' or y == 'Not evaluable':
        row_to_drop.append(i)

data = data.drop(row_to_drop)

# replace '-' to NaN and rename categories to floats

for x in data.columns:
    column = data[x]
    if column.dtype == 'O':
        column = column.astype('category')

        if '-' in column.cat.categories:
            column[column == '-'] = np.nan
            column = column.cat.remove_categories('-')

        saved_category_names[x] = [y.encode('utf-8') for y in list(column.cat.categories)]
        data[x] = column.cat.rename_categories([str(x) for x in range(len(column.cat.categories))])
        # elif not x == 0:
        #     data[x], saved_bins[x] = pd.cut(column, 3, retbins=True, labels=['1', '2', '3'])
        print(x, '->', saved_column_names[x], '->', *saved_category_names[x])
    else:
        print(x, '->', saved_column_names[x])

