"""Generate a workspace reading copy; PROJECT.md in the repository is canonical."""

import argparse
from pathlib import Path
import re
import stat


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace-root', type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    workspace = args.workspace_root.resolve(strict=True)
    if not workspace.is_dir() or repo.parent != workspace:
        parser.error('workspace-root must be the parent directory of this checkout')
    source = repo / 'PROJECT.md'
    target = workspace / 'PROJECT.md'
    if target.is_symlink() or (target.exists() and
            getattr(target.lstat(), 'st_file_attributes', 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT):
        parser.error('refusing to replace a linked workspace document')
    content = source.read_text(encoding='utf-8')

    def workspace_link(match):
        label, url = match.group(1), match.group(2)
        if re.match(r'[A-Za-z][A-Za-z0-9+.-]*:', url) or url.startswith(('#', '/')):
            return match.group(0)
        return f'[{label}]({repo.name}/{url})'

    content = re.sub(r'\[([^\]\n]+)\]\(([^\s)]+)\)', workspace_link, content)
    note = (f'> 此文件为自动生成的工作区阅读副本。请编辑 '
            f'[{repo.name}/PROJECT.md]({repo.name}/PROJECT.md)，'
            '再运行 tools/sync_project_document.py 同步；不要单独修改此副本。\n\n')
    target.write_text(note + content, encoding='utf-8')
    print(f'Updated {target}')


if __name__ == '__main__':
    main()
