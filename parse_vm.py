import re

with open('decompiled_output/sources/com/example/viewmodel/AppViewModel.java', 'r') as f:
    text = f.read()

# Find all MutableStateFlow assignments
mutable_flows = re.findall(r'this\.(_\w+)\s*=\s*StateFlowKt\.MutableStateFlow\((.*?)\);', text)
print(f"Total mutable state flows: {len(mutable_flows)}")
for name, val in mutable_flows:
    print(f"val {name} = MutableStateFlow({val})")
