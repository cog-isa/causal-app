import subprocess
import sys
import re
from aq.aq_description import Fact, Rule, ClassDescription


def run_aq(data, class_column, column_names):
    input_text = _generate_input(data, class_column)

    file_name = 'input.aq21'
    f = open(file_name, 'w')
    f.write(input_text)
    f.flush()

    ex_name = './aq/aq21' if sys.platform == 'linux' else 'aq/aq21.exe'
    output = subprocess.Popen([ex_name, file_name], stdout=subprocess.PIPE).communicate()[0].decode('utf-8')

    descriptions = _parse_result(output, column_names)

    return descriptions


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


def _parse_result(result, column_names):
    class_regex = re.compile(r'Output_Hypotheses rules_for_(\d+)\s+')
    num_regex = re.compile(r'Number of rules in the cover = (\d+)\s+')
    rule_regex = re.compile(r'# Rule (\d+)\s+<--([^:]+)')
    part_regex = re.compile(r'\s*\[attr_(\d+)=(\S+)\]')
    stat_regex = re.compile(r': p=(\d+),np=(\d+),n=(\d+),q=(\d+\.\d+),cx=(\d+),c=(\d+),s=(\d+) #')

    class_matcher = class_regex.findall(result)
    num_matcher = num_regex.findall(result)
    rule_matcher = rule_regex.findall(result)
    stat_matcher = stat_regex.findall(result)

    descriptions = {}
    if class_matcher and num_matcher and rule_matcher and stat_matcher:
        rule_nums = list(map(int, num_matcher))

        classes_for_rules = []
        for (name, nums) in zip(class_matcher, rule_nums):
            d = ClassDescription(name, [])
            descriptions[name] = d
            classes_for_rules.extend([d] * nums)

        for i, ((rule_id, rule), (p, np, n, q, cx, c, s)) in enumerate(zip(rule_matcher, stat_matcher)):
            r = Rule(int(rule_id), [])
            r.covered_positives = int(p)
            r.covered_negatives = int(n)
            r.complexity = int(cx)
            r.cost = int(c)
            r.significance = int(s)
            part_matcher = part_regex.findall(rule)
            if part_matcher:
                for (attr_id, value) in part_matcher:
                    f = Fact(int(attr_id), set(map(int, value.split(','))), column_names[int(attr_id)])
                    r.facts.append(f)

            classes_for_rules[i].rules.append(r)

    return descriptions
