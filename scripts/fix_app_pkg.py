import json

pkg_path = '/Users/luohuan/Documents/trae_projects/mp-/star-ai-app/package.json'
with open(pkg_path) as f:
    p = json.load(f)

p['dependencies']['react'] = '18.3.1'
p['dependencies']['react-dom'] = '18.3.1'
p['devDependencies']['@types/react'] = '18.3.12'
p['devDependencies']['@types/react-dom'] = '18.3.1'
p['devDependencies'].pop('next-auth', None)

with open(pkg_path, 'w') as f:
    json.dump(p, f, indent=2)

print('Fixed React version to 18.3.1')
