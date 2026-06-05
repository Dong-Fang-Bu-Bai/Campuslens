/**
 * 农历（阴阳合历）查表转换工具
 * 支持年份范围：1900 - 2100
 */

// 1900-2100 年的农历数据表
const lunarInfo = [
  0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16547, 0x056a0, 0x09ad0, 0x055d2,
  0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
  0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
  0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
  0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
  0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
  0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
  0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
  0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
  0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
  0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
  0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
  0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
  0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
  0x0b540, 0x0d6a0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
  0x14b63
];

const lunarMonths = ["正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "腊"];
const lunarDays = [
  "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
  "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
  "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
];
const tiangan = ["甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"];
const dizhi = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"];
const shengxiao = ["鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"];

// 获取农历某年的总天数
function getLunarYearDays(y) {
  let sum = 348;
  for (let i = 0x8000; i > 0x8; i >>= 1) {
    sum += (lunarInfo[y - 1900] & i) ? 1 : 0;
  }
  return sum + getLeapMonthDays(y);
}

// 获取农历某年闰月是几月（1-12，若无闰月返回0）
function getLeapMonth(y) {
  return lunarInfo[y - 1900] & 0xf;
}

// 获取农历某年闰月的天数
function getLeapMonthDays(y) {
  if (getLeapMonth(y)) {
    return (lunarInfo[y - 1900] & 0x10000) ? 30 : 29;
  }
  return 0;
}

// 获取农历某年某月的天数
function getMonthDays(y, m) {
  return (lunarInfo[y - 1900] & (0x10000 >> m)) ? 30 : 29;
}

/**
 * 转换公历日期为农历对象
 * @param {Date} date 
 */
export function getLunar(date) {
  let i, leap = 0, temp = 0;
  // 1900年1月31日是农历正月初一
  const baseDate = new Date(1900, 0, 31);
  let offset = Math.floor((date.getTime() - baseDate.getTime()) / 86400000);

  let lunarYear = 1900;
  for (i = 1900; i < 2100 && offset > 0; i++) {
    temp = getLunarYearDays(i);
    offset -= temp;
    lunarYear++;
  }

  if (offset < 0) {
    offset += temp;
    lunarYear--;
  }

  leap = getLeapMonth(lunarYear);
  let isLeap = false;

  let lunarMonth = 1;
  for (i = 1; i < 13 && offset > 0; i++) {
    if (leap > 0 && i === (leap + 1) && !isLeap) {
      --i;
      isLeap = true;
      temp = getLeapMonthDays(lunarYear);
    } else {
      temp = getMonthDays(lunarYear, i);
    }

    if (isLeap && i === (leap + 1)) {
      isLeap = false;
    }

    offset -= temp;
    if (!isLeap) {
      lunarMonth++;
    }
  }

  if (offset < 0) {
    offset += temp;
    if (isLeap) {
      isLeap = false;
    } else {
      lunarMonth--;
    }
  }

  if (offset === 0 && leap > 0 && lunarMonth === leap + 1) {
    if (isLeap) {
      isLeap = false;
    } else {
      isLeap = true;
      lunarMonth--;
    }
  }

  const lunarDay = offset + 1;
  return {
    year: lunarYear,
    month: lunarMonth,
    day: lunarDay,
    isLeap: isLeap
  };
}

/**
 * 获取格式化好的农历日期字符串
 * @param {Date} date 
 * @returns {string} 比如：丙午年 (马年) 闰五月十三
 */
export function getLunarDateString(date) {
  const lunar = getLunar(date);
  
  const tg = tiangan[(lunar.year - 4) % 10];
  const dz = dizhi[(lunar.year - 4) % 12];
  const sx = shengxiao[(lunar.year - 4) % 12];
  
  const monthStr = (lunar.isLeap ? "闰" : "") + lunarMonths[lunar.month - 1] + "月";
  const dayStr = lunarDays[lunar.day - 1];
  
  return `${tg}${dz}年 (${sx}年) ${monthStr}${dayStr}`;
}
