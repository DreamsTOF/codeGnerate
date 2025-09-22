/**
 * 计算两个文本之间的差异
 * @param fromLines 原始文本行数组
 * @param toLines 目标文本行数组
 * @returns 差异数组
 */
export interface DiffResult {
  type: 'added' | 'removed' | 'unchanged'
  content: string
  fromLine?: number
  toLine?: number
}

export function calculateDiff(fromLines: string[], toLines: string[]): DiffResult[] {
  const diffs: DiffResult[] = []

  const maxLines = Math.max(fromLines.length, toLines.length)

  for (let i = 0; i < maxLines; i++) {
    const fromLine = fromLines[i]
    const toLine = toLines[i]

    if (fromLine === toLine) {
      // 未变化的行
      if (fromLine !== undefined) {
        diffs.push({
          type: 'unchanged',
          content: fromLine,
          fromLine: i + 1,
          toLine: i + 1
        })
      }
    } else if (fromLine === undefined) {
      // 新增的行
      diffs.push({
        type: 'added',
        content: toLine,
        toLine: i + 1
      })
    } else if (toLine === undefined) {
      // 删除的行
      diffs.push({
        type: 'removed',
        content: fromLine,
        fromLine: i + 1
      })
    } else {
      // 修改的行
      diffs.push({
        type: 'removed',
        content: fromLine,
        fromLine: i + 1
      })
      diffs.push({
        type: 'added',
        content: toLine,
        toLine: i + 1
      })
    }
  }

  return diffs
}

/**
 * 测试差异计算功能
 */
export function testDiffCalculation(): boolean {
  const fromText = [
    'function hello() {',
    '  console.log("Hello, World!");',
    '  return "Hello";',
    '}'
  ]

  const toText = [
    'function hello() {',
    '  console.log("Hello, Universe!");',
    '  return "Hello";',
    '  // Add a comment',
    '}'
  ]

  const diffs = calculateDiff(fromText, toText)

  // 验证差异结果
  const hasChangedLine = diffs.some(diff =>
    diff.type === 'removed' && diff.content.includes('Hello, World!')
  )

  const hasAddedLine = diffs.some(diff =>
    diff.type === 'added' && diff.content.includes('Hello, Universe!')
  )

  const hasNewComment = diffs.some(diff =>
    diff.type === 'added' && diff.content.includes('Add a comment')
  )

  return hasChangedLine && hasAddedLine && hasNewComment
}