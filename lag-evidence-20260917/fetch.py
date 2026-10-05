import getpass
import json
import stat
import sys
from datetime import datetime, timezone
from pathlib import Path

import paramiko


host, user, mode, *paths = sys.argv[1:]
password = getpass.getpass('SFTP password: ')
client = paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(host, port=2022, username=user, password=password,
               look_for_keys=False, allow_agent=False, timeout=20,
               auth_timeout=20, banner_timeout=20)
del password
sftp = client.open_sftp()
sftp.get_channel().settimeout(45)
dest = Path(__file__).parent
try:
    for path in paths or ['.']:
        if mode == 'list':
            print('DIRECTORY', path, flush=True)
            try:
                entries = sftp.listdir_attr(path)
            except OSError as exc:
                print(type(exc).__name__, str(exc), flush=True)
                continue
            for entry in sorted(entries, key=lambda e: e.filename):
                print(json.dumps({
                    'name': entry.filename,
                    'dir': stat.S_ISDIR(entry.st_mode),
                    'bytes': entry.st_size,
                    'modified_utc': datetime.fromtimestamp(
                        entry.st_mtime, timezone.utc).isoformat(),
                }), flush=True)
        elif mode == 'get':
            target = (dest / 'server' / path.lstrip('/')).resolve()
            if not target.is_relative_to((dest / 'server').resolve()):
                raise ValueError('path outside evidence folder')
            target.parent.mkdir(parents=True, exist_ok=True)
            sftp.get(path, str(target))
            print('SAVED', path, target.stat().st_size, flush=True)
        else:
            raise ValueError('unknown mode')
finally:
    sftp.close()
    client.close()
