import pandas as pd

column_ranges = {}
column_names = []


def load_data(file_path):
    data = pd.read_csv(file_path, encoding='cp1251', sep=';', index_col=False, na_values='?', decimal=',')
    column_names.extend(list(data.columns))
    data.columns = ['attr_' + str(x) for x in range(data.shape[1])]

    # categorize nominal column 0
    data.iloc[:, 0] = data.iloc[:, 0].astype('category')
    data.iloc[:, 0] = data.iloc[:, 0].cat.rename_categories(['1', '2'])

    # discretize all non categorical columns
    for column in data.columns:
        if not data[column].dtype.name == 'category':
            data[column] = pd.cut(data[column], 3)
            column_ranges[column] = data[column].cat.categories
            data[column] = data[column].cat.rename_categories(['1', '2', '3'])

    return data, 'attr_3'
