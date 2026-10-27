#!/usr/bin/env python3
"""Create the local Android release identity once, without exposing its password."""
import os
from pathlib import Path
import secrets
import subprocess
import tempfile


def main():
    directory = Path.home() / '.android' / 'pool-signing'
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory.chmod(0o700)
    keystore = directory / 'release.jks'
    properties = directory / 'signing.properties'
    if keystore.exists() or properties.exists():
        raise SystemExit('Signing material already exists; refusing to overwrite it.')
    password = secrets.token_urlsafe(48)
    java_home = os.environ.get('JAVA_HOME')
    keytool = str(Path(java_home) / 'bin' / 'keytool') if java_home else 'keytool'
    # Restrict every newly created secret, including the temporary keytool password file.
    previous_mask = os.umask(0o077)
    try:
        with tempfile.NamedTemporaryFile(mode='w', dir=directory) as password_file:
            password_file.write(password)
            password_file.flush()
            subprocess.run([
                keytool, '-genkeypair', '-keystore', str(keystore), '-storetype', 'PKCS12',
                '-alias', 'pool-release', '-keyalg', 'RSA', '-keysize', '3072',
                '-validity', '36500', '-dname', 'CN=Feltri 8-Ball',
                '-storepass:file', password_file.name, '-keypass:file', password_file.name,
            ], check=True)
        with properties.open('x') as output:
            output.write(f'storeFile={keystore}\nstorePassword={password}\n'
                         f'keyAlias=pool-release\nkeyPassword={password}\n')
    finally:
        os.umask(previous_mask)
    print(f'Created signing identity in {directory}. Back up both files securely; see docs/releases.md.')


if __name__ == '__main__':
    main()
