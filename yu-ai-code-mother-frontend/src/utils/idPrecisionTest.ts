/**
 * 测试ID精度处理
 * 用于验证大数字ID在JavaScript中的处理是否正确
 */

/**
 * 测试大数字ID的字符串处理
 */
export function testLargeNumberIdHandling(): boolean {
  // 模拟一个大数字ID（可能超过JavaScript安全整数范围）
  const largeIdString = "12345678901234567890"

  // 测试直接转换为Number是否会丢失精度
  const asNumber = Number(largeIdString)
  const backToString = String(asNumber)

  // 如果转换前后不一致，说明精度丢失了
  const precisionLost = largeIdString !== backToString

  console.log('原始ID字符串:', largeIdString)
  console.log('转换为数字:', asNumber)
  console.log('转回字符串:', backToString)
  console.log('精度丢失:', precisionLost)

  // 验证字符串处理不会丢失精度
  const preservedPrecision = largeIdString === String(largeIdString)

  return preservedPrecision && !precisionLost
}

/**
 * 测试版本ID的正确处理方式
 */
export function testVersionIdHandling(): boolean {
  // 模拟版本ID
  const versionIds = ["12345678901234567890", "98765432109876543210"]

  // 测试选择逻辑（使用字符串）
  const selectedVersions: Record<string, boolean> = {
    [versionIds[0]]: true,
    [versionIds[1]]: true
  }

  const selectedIds = Object.entries(selectedVersions)
    .filter(([_, selected]) => selected)
    .map(([id, _]) => id)

  // 验证选中的ID是否正确
  const allIdsPreserved = selectedIds.every(id =>
    versionIds.includes(id) && id === String(id)
  )

  console.log('选中的版本ID:', selectedIds)
  console.log('ID保持完整:', allIdsPreserved)

  return allIdsPreserved && selectedIds.length === 2
}

/**
 * 运行所有测试
 */
export function runIdPrecisionTests(): boolean {
  console.log('开始ID精度测试...')

  const test1 = testLargeNumberIdHandling()
  const test2 = testVersionIdHandling()

  const allTestsPassed = test1 && test2

  console.log('所有测试通过:', allTestsPassed)

  return allTestsPassed
}