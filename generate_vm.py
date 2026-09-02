import re

with open('decompiled_output/sources/com/example/viewmodel/AppViewModel.java', 'r') as f:
    java_code = f.read()

# Let's inspect data classes inside AppViewModel.java
