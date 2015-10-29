__author__ = 'Aleksandr Panov'
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# load data and rename columns
data = pd.read_csv('real_sample.csv', encoding='utf-8')
col_len = data.shape[1]
data.columns = ['attr_' + str(x) for x in range(col_len)]

# list([data['attr_'+str(x)].dtype for x in range(col_len)])

# convert strings to category types
for i in range(col_len):
    if data['attr_'+str(i)].dtype == 'O':
        data['attr_'+str(i)] = data['attr_'+str(i)].astype('category')

# replace '-' to NaN
for column in [data[x] for x in data.columns]:
    if hasattr(column, 'cat') and '-' in column.cat.categories:
        column[column == '-'] = np.nan
        column.cat.remove_categories('-')
