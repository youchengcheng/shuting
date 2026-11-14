import test from 'node:test'
import assert from 'node:assert/strict'
import { notificationPreview } from '../src/utils/notificationPresentation.js'

const item = (type, preview, contentStatus = 'AVAILABLE') => ({ type, preview, contentStatus })

test('a comment like shows the liked comment with its target note cover', () => {
  assert.deepEqual(notificationPreview(item('COMMENT_LIKE', { summary: '被点赞的那条评论', title: '所属笔记的标题', cover: 'note-cover' })),
    { text: '被点赞的那条评论', imageUrl: '', cover: 'note-cover' })
})

test('new comments and replies show the incoming content', () => {
  for (const type of ['COMMENT', 'REPLY']) {
    assert.deepEqual(notificationPreview(item(type, { summary: '对方刚发来的内容', title: '笔记标题', cover: 'note-cover' })),
      { text: '对方刚发来的内容', imageUrl: '', cover: 'note-cover' })
  }
})

test('a picture comment shows its image and target note cover without unrelated text', () => {
  for (const type of ['COMMENT_LIKE', 'COMMENT', 'REPLY']) {
    assert.deepEqual(notificationPreview(item(type, { imageUrl: 'comment-picture', summary: '同一评论的文字', title: '笔记标题', cover: 'note-cover' })),
      { text: '', imageUrl: 'comment-picture', cover: 'note-cover' })
  }
})

test('unavailable content never exposes cached text or pictures', () => {
  for (const status of ['UNAVAILABLE', 'TEMPORARY_UNAVAILABLE']) {
    const result = notificationPreview(item('COMMENT_LIKE', { summary: '旧评论', imageUrl: 'old-picture', cover: 'old-cover' }, status))
    assert.equal(result.imageUrl, '')
    assert.equal(result.cover, '')
    assert.notEqual(result.text, '旧评论')
  }
})

test('a legacy or empty comment preview does not substitute the note title', () => {
  for (const summary of [undefined, '', '  \n ']) {
    assert.equal(notificationPreview(item('COMMENT_LIKE', { title: '不相关的笔记标题', summary })).text, '查看评论')
  }
})

test('note likes and collections retain their note title and thumbnail', () => {
  for (const type of ['NOTE_LIKE', 'NOTE_COLLECT']) {
    assert.deepEqual(notificationPreview(item(type, { title: '笔记标题', cover: 'note-cover' })),
      { text: '笔记标题', imageUrl: '', cover: 'note-cover' })
  }
})

test('a follow notification has no content preview', () => {
  assert.deepEqual(notificationPreview(item('FOLLOW', { title: '旧附加内容', cover: 'old-cover', imageUrl: 'old-picture' })),
    { text: '', imageUrl: '', cover: '' })
})
