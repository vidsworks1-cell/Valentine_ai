import re

# Read the decompiled Java file
with open('decompiled_output/sources/com/example/viewmodel/AppViewModel.java', 'r') as f:
    src = f.read()

# Let's inspect the imports from Java file
imports = set()
for line in src.splitlines():
    if line.startswith('import '):
        imp = line.strip().replace('import ', '').replace(';', '')
        if not imp.startswith('kotlin.jvm.internal') and not imp.startswith('androidx.compose.runtime.Composer') and not imp.startswith('androidx.credentials') and not imp.startswith('androidx.exifinterface'):
            imports.add(imp)

print(f"Collected {len(imports)} imports.")
