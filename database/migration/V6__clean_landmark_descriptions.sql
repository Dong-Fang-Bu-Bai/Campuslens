-- 清洗地标描述与摘要中开发和检索测试痕迹，升级为更具人文色彩的高端导览词句

-- 1. 图书馆 (L01)
UPDATE landmark 
SET summary = '校园核心文化与学术中心，拥有独特的书页外立面结构。',
    description = '图书馆位于文雍广场北侧，是全校藏书丰富、自习与学术研讨的核心场所。建筑气势宏伟，外立面呈半开卷书页状，是校园最具标志性的文化地标。',
    cover_image_url = 'https://images.unsplash.com/photo-1521587760476-6c12a4b040da?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L01';

-- 2. 学术大讲堂 (L02)
UPDATE landmark 
SET summary = '举办大型学术报告与校园文娱盛典的多功能现代化场馆。',
    description = '学术大讲堂邻近东门，是学校举办大型学术报告、文化盛典及师生集中教学活动的主阵地。其弧形入口极具现代感与辨识度。',
    cover_image_url = 'https://images.unsplash.com/photo-1492538368577-870624790c4a?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L02';

-- 3. 文雍广场 (L03)
UPDATE landmark 
SET summary = '开阔宽广的标志性休闲广场，是校园人文景观的核心纽带。',
    description = '文雍广场坐落于图书馆南侧，是一座融绿化、喷泉与休闲步道于一体的开阔广场，为校园师生举行集会和课余小憩的重要集散地。',
    cover_image_url = 'https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L03';

-- 4. 博学桥 (L04)
UPDATE landmark 
SET summary = '横跨韵湖的典雅观景石桥，连接南北主要功能园区。',
    description = '博学桥横跨在美丽的韵湖之上，将教学区与生活区优雅连通。桥身造型典雅，与湖面交相辉映，是备受师生喜爱的校园写意景观。',
    cover_image_url = 'https://images.unsplash.com/photo-1549880338-65ddcdfd017b?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L04';

-- 5. 琴湖及湖心岛 (L05)
UPDATE landmark 
SET summary = '环境幽雅的水域景观，湖水碧绿，岛上植被常青。',
    description = '琴湖及湖心岛位于文雍路东侧，水体清澈，绿化茂密。清晨和傍晚，这里烟波浩渺，是校园内最富有自然诗意和静谧之美的一隅。',
    cover_image_url = 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L05';

-- 6. 体育馆 (L06)
UPDATE landmark 
SET summary = '配备多功能运动场地的现代化综合室内健身体育馆。',
    description = '体育馆位于文雍路西侧，是一座设施完善的现代化多功能场馆，服务全校体育教学、文体赛事和日常锻炼，极具动感的网架几何外形十分夺目。',
    cover_image_url = 'https://images.unsplash.com/photo-1577416412292-747c6607f055?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L06';

-- 7. 游泳馆 (L07)
UPDATE landmark 
SET summary = '配备先进循环系统的室内温水游泳馆。',
    description = '游泳馆位于体育馆北侧，配有标准泳道和水循环 system，是日常游泳教学、水上运动训练以及师生消暑运动的首选场馆。',
    cover_image_url = 'https://images.unsplash.com/photo-1519766304817-4f37bda74a27?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L07';

-- 8. 第一饭堂 (L08)
UPDATE landmark 
SET summary = '大众膳食生活服务中心，提供多风味特色餐饮。',
    description = '第一饭堂位于尚学路西侧，汇集了全国各地的特色美食与实惠膳食，是学生日常用餐和生活交流的主要生活服务场所。',
    cover_image_url = 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L08';

-- 9. 第二饭堂 (L09)
UPDATE landmark 
SET summary = '东区师生自选精致餐饮中心，兼备休闲社交空间。',
    description = '第二饭堂邻近东二门，内部设有现代化的自选餐厅与休闲卡座，主打精品小吃和社交就餐，为东区师生提供高品质膳食体验。',
    cover_image_url = 'https://images.unsplash.com/photo-1578474846511-04ba529f0b88?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L09';

-- 10. 中心酒店 (L10)
UPDATE landmark 
SET summary = '校内接待与住宿场所，设施齐全服务高档。',
    description = '中心酒店位于北门内侧，主要用于校内接待和住宿服务，大厅宽敞，周边绿化环抱，为来访专家和宾客提供舒适静谧的居住环境。',
    cover_image_url = 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80'
WHERE code = 'L10';
