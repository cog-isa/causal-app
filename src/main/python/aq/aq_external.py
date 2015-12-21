import subprocess
import sys
import re


def run_aq(data, class_column):
    input_text = _generate_input(data, class_column)

    file_name = 'input.aq21'
    f = open(file_name, 'w')
    f.write(input_text)
    f.flush()

    ex_name = './aq/aq21' if sys.platform == 'linux' else 'aq/aq21.exe'
    output = subprocess.Popen([ex_name, file_name], stdout=subprocess.PIPE).communicate()[0].decode('utf-8')

    _parse_result(output)


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


def _generate_runs(data, class_column):
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
    result = data.to_csv(None, sep=',', na_rep='?', index=False, header=False)
    return result


def _generate_input(data, class_column):
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
    text += _generate_runs(data, class_column)
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


def _parse_result(result):
    num_regex = re.compile(r'Number of rules in the cover = (\d+)\s+')
    rule_regex = re.compile(r'# Rule (\d+)\s+<--([^:]+)')
    part_regex = re.compile(r'\s*\[attr_(\d+)=(\S+)\]')

    num_matcher = num_regex.findall(result)
    rule_matcher = rule_regex.findall(result)

    if num_matcher and rule_matcher:
        rule_nums = list(map(int, num_matcher))
        rule_ids = [int(x) for (x, _) in rule_matcher]
        rules = [y for (_, y) in rule_matcher]

        for rule in rules:
            part_matcher = part_regex.findall(rule)
            if part_matcher:
                attr_ids = [int(x) for (x, _) in part_matcher]
                values = [y for (_, y) in part_matcher]
