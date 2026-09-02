import sys, re

# Let's inspect the methods in AppViewModel.java
with open('decompiled_output/sources/com/example/viewmodel/AppViewModel.java', 'r') as f:
    text = f.read()

print("File size:", len(text))
