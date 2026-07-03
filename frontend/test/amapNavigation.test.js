import test from 'node:test'
import assert from 'node:assert/strict'
import { buildAmapWalkingNavigationUrl } from '../src/utils/amapNavigation.js'

test('builds an app-first AMap walking route with an encoded Chinese destination', () => {
  const result = buildAmapWalkingNavigationUrl({
    code: 'L01',
    name: '华工图书馆',
    longitude: 113.405412,
    latitude: 23.046547
  })
  const url = new URL(result)

  assert.equal(url.origin + url.pathname, 'https://uri.amap.com/navigation')
  assert.equal(url.searchParams.get('from'), '')
  assert.equal(url.searchParams.get('to'), '113.405412,23.046547,华工图书馆')
  assert.equal(url.searchParams.get('mode'), 'walk')
  assert.equal(url.searchParams.get('callnative'), '1')
})

test('does not build navigation for missing, partial, or invalid coordinates', () => {
  assert.equal(buildAmapWalkingNavigationUrl({ name: '未配置' }), null)
  assert.equal(buildAmapWalkingNavigationUrl({ name: '只有经度', longitude: 113.4 }), null)
  assert.equal(buildAmapWalkingNavigationUrl({ name: '非法纬度', longitude: 113.4, latitude: 100 }), null)
})
