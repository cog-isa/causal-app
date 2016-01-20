text = """28
21
29
22
18
21
28
12
12
22
17
19
16
27
19
23
10
16
22
20
23
15
26
14
23
15
27
12
17
28
23
23
23
19
20
19
26
13
25
22
11
22
19
25
12
11
26
20
27
14
21
21
29
14
27
27
17
15
23
10
28
22
29
20
15
10
15
20
21
24
31
23
26
17
19
10
17
28
23
18
14
17
26
21
28
24"""

nums = list(map(int, text.split('\n')))

ma = max(nums)
mi = min(nums)
av1 = mi + (ma - mi) / 3
av2 = mi + 2 * (ma - mi) / 3

res = [1 if x < av1 else 2 if x < av2 else 3 for x in nums]
print(*res, sep='\n')
