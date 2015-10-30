__author__ = 'Aleksandr Panov'

import subprocess

file_name = 'input.aq21'
output = subprocess.Popen(['aq21.exe', file_name], stdout=subprocess.PIPE).communicate()[0]

print(output)
