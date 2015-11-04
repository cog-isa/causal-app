__author__ = 'Aleksandr Panov'

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

input_file = 'real_sample.csv'
output_file = 'real.gqj'
saved_column_names = []
saved_category_names = {}
saved_bins = {}

target_column = 35

# load data and rename columns
data = pd.read_csv(input_file, encoding='utf-8')
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

data = data.dropna(subset=[target_column])

f = open(output_file, 'w')
f.write(str(target_column - 1) + '\n' + ','.join([str(x) for x in saved_category_names]) + '\n')
f.write(';'.join([str(x) + ':' + ','.join(list(data[x].cat.categories)) for x in saved_category_names]) + '\n')
f.flush()

data.to_csv(output_file, sep='\t', na_rep='?', index=False,
            columns=range(1, col_len), mode='a')
