path = '/Users/luohuan/Documents/trae_projects/mp-/star-ai-app/src/app/video/[id]/page.tsx'
with open(path) as f:
    c = f.read()

old = """export default function VideoDetailPage({ params }: { params: { id: string } }) {
  if (!params.id) notFound()"""

new = """export default async function VideoDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  if (!id) notFound()"""

c = c.replace(old, new)
c = c.replace("params.id", "id")

with open(path, 'w') as f:
    f.write(c)
print('Fixed video/[id]/page.tsx')
