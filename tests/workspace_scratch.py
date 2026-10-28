"""Workspace-scoped synthetic fixtures with inherited Windows runner permissions."""
from contextlib import contextmanager
from pathlib import Path
import shutil
import uuid

BUILD = (Path(__file__).resolve().parents[1] / 'build').resolve()


def make_scratch(prefix='fixture-', dir=None):
    base = Path(dir).resolve() if dir is not None else BUILD / 'test-scratch'
    if base != BUILD and BUILD not in base.parents:
        raise ValueError('Test scratch must stay inside workspace build')
    if Path(prefix).name != prefix or '/' in prefix or '\\' in prefix:
        raise ValueError('Invalid scratch prefix')
    base.mkdir(parents=True, exist_ok=True)
    directory = base / (prefix + uuid.uuid4().hex)
    # Python 3.13's Windows 0700 ACL can exclude restricted runner tokens.
    # Only synthetic test data goes here; inherit the build directory ACL.
    directory.mkdir()
    return str(directory)


@contextmanager
def scratch_directory(prefix='fixture-', dir=None):
    directory = Path(make_scratch(prefix, dir)).resolve()
    base = directory.parent
    try:
        yield str(directory)
    finally:
        if directory.is_symlink() or directory.resolve().parent != base or BUILD not in base.parents and base != BUILD:
            raise ValueError('Refusing cleanup outside the test scratch directory')
        shutil.rmtree(directory)
