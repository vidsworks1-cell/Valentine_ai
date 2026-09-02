import re

# Read decompiled Java code
with open('decompiled_output/sources/com/example/viewmodel/AppViewModel.java', 'r') as f:
    src = f.read()

print("Read AppViewModel.java successfully")
