const AMAP_NAVIGATION_URL = 'https://uri.amap.com/navigation'

function validCoordinate(value, min, max) {
  if (value === null || value === undefined || value === '') return false
  const number = Number(value)
  return Number.isFinite(number) && number >= min && number <= max
}

export function buildAmapWalkingNavigationUrl(landmark) {
  if (!landmark
      || !validCoordinate(landmark.longitude, -180, 180)
      || !validCoordinate(landmark.latitude, -90, 90)) {
    return null
  }

  const url = new URL(AMAP_NAVIGATION_URL)
  url.searchParams.set('from', '')
  url.searchParams.set('to', `${Number(landmark.longitude)},${Number(landmark.latitude)},${landmark.name || landmark.code || 'CampusLens 地标'}`)
  url.searchParams.set('mode', 'walk')
  url.searchParams.set('policy', '0')
  url.searchParams.set('src', 'CampusLens')
  url.searchParams.set('callnative', '1')
  return url.toString()
}
