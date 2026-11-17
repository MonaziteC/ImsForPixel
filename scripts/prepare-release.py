#!/usr/bin/env python3
"""Package a manually approved, SHA-256-pinned APK and the current source for a draft release."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlparse
import zipfile


def run(*args):
    return subprocess.run(args, check=True, capture_output=True)


def main():
    tag = os.environ['RELEASE_TAG']
    url = os.environ['APK_URL']
    expected = os.environ['APK_SHA256'].lower()
    if not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?', tag):
        raise ValueError('Invalid version tag')
    if not re.fullmatch(r'[a-f0-9]{64}', expected):
        raise ValueError('Expected SHA-256 is required')
    if urlparse(url).scheme != 'https':
        raise ValueError('An HTTPS download URL is required')
    version = tag[1:]
    out = Path('publish')
    out.mkdir(exist_ok=True)
    apk = out / f'pixel-ims-{version}.apk'
    run('curl', '--fail', '--location', '--silent', '--show-error', '--max-time', '180', url, '-o', str(apk))
    # tmpfiles.org returns an HTML download page. Resolve only its same-host /dl/ link;
    # never evaluate page content or execute the downloaded APK.
    if urlparse(url).hostname == 'tmpfiles.org' and not zipfile.is_zipfile(apk):
        page = apk.read_text()
        links = re.findall(r'href=["\']([^"\']+)["\']', page)
        direct = next((link for link in links if urlparse(link).scheme == 'https'
                       and urlparse(link).hostname == 'tmpfiles.org'
                       and urlparse(link).path.startswith('/dl/')), None)
        if not direct:
            raise ValueError('Download page has no expected APK link')
        run('curl', '--fail', '--location', '--silent', '--show-error', '--max-time', '180', direct, '-o', str(apk))
    if hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
        raise ValueError('APK SHA-256 does not match the approved artifact')
    if not zipfile.is_zipfile(apk):
        raise ValueError('APK is not a ZIP-format Android package')
    source = out / f'pixel-ims-{version}-source.zip'
    run('git', 'archive', '--format=zip', '--prefix=PixelIMS/', '--output=' + str(source), 'HEAD')
    bundle = out / f'pixel-ims-{version}.bundle'
    run('git', 'bundle', 'create', str(bundle), '--all')
    history = out / f'pixel-ims-{version}-git.zip'
    with zipfile.ZipFile(history, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.write(bundle, bundle.name)
        archive.writestr('README.txt', f'Extract and run: git clone {bundle.name} PixelIMS\nSee FORK_GUIDE.md.\n')
    assets = [apk, source, history]
    (out / 'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in assets))
    print(f'Prepared {tag}; verified APK SHA-256 {expected}')


if __name__ == '__main__':
    main()
