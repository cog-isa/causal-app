__author__ = 'Aleksandr Panov'

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

saved_column_names = []
saved_category_names = {}
saved_bins = {}

# load data and rename columns
data = pd.read_csv('real_sample.csv', encoding='utf-8')
col_len = data.shape[1]
saved_column_names.extend(list(data.columns))
data.columns = [x for x in range(col_len)]

# replace '-' to NaN and rename categories to floats

for x in data.columns:
    column = data[x]
    if column.dtype == 'O':
        column = column.astype('category')

        if '-' in column.cat.categories:
            column[column == '-'] = np.nan
            column = column.cat.remove_categories('-')

        saved_category_names[x] = list(column.cat.categories)
        data[x] = column.cat.rename_categories([str(x) for x in range(len(column.cat.categories))])
        # elif not x == 0:
        #     data[x], saved_bins[x] = pd.cut(column, 3, retbins=True, labels=['1', '2', '3'])
        print(x, '->', saved_column_names[x])

