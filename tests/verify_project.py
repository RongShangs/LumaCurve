"""Production framework verification entry. Historical checks live in verify_native_reference.py."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).resolve().parents[1]/'tools/verify_framework_project.py'),run_name='__main__')
