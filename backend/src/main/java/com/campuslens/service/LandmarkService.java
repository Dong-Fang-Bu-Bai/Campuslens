package com.campuslens.service;

import com.campuslens.model.LandmarkDetail;
import com.campuslens.model.LandmarkImage;
import com.campuslens.model.LandmarkSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LandmarkService {
  private final List<LandmarkDetail> landmarks = List.of(
      landmark(1L, "L01", "图书馆", "Library", "建筑",
          "校园核心学习空间，建筑体量大、外立面辨识度高。",
          "图书馆位于文雍广场附近，是学生自习、借阅和课程资料检索的主要场所。建筑正立面开阔，适合作为图像检索的代表性样本。",
          "文雍广场附近", 50.31, 59.33),
      landmark(2L, "L02", "学术大讲堂", "Academic Auditorium", "建筑",
          "大型报告与答辩活动场所，适合作为答辩演示样本。",
          "学术大讲堂靠近东门，常用于学术报告、会议与集中教学活动，入口与外立面具有较强辨识度。",
          "东门附近", 62.16, 61.22),
      landmark(3L, "L03", "文雍广场", "Wenyong Square", "广场",
          "校园开放空间与人流汇聚点，便于地图静态标注。",
          "文雍广场位于图书馆前，是校园公共活动与通行的重要节点，适合作为室外场景识别对象。",
          "图书馆前", 57.37, 63.56),
      landmark(4L, "L04", "博学桥", "Boxue Bridge", "桥梁",
          "连接湖区两侧的桥梁景观，适合作为地标景观样本。",
          "博学桥位于韵湖沿线，桥体、湖面和周边道路共同形成稳定的视觉特征。",
          "韵湖沿线", 57.75, 46.17),
      landmark(5L, "L05", "琴湖及湖心岛", "Qin Lake / Huxin Island", "湖区",
          "湖泊与岛屿组合景观，外观特征明显。",
          "琴湖及湖心岛位于文雍路东侧，水域、绿化和湖心岛轮廓适合进行地标图像检索。",
          "文雍路东侧", 67.32, 26.88),
      landmark(6L, "L06", "体育馆", "Stadium", "场馆",
          "体育活动场馆，建筑边界清晰。",
          "体育馆位于文雍路西侧，服务课程教学、赛事活动和学生日常锻炼，场馆外形便于识别。",
          "文雍路西侧", 43.88, 49.07),
      landmark(7L, "L07", "游泳馆", "Natatorium", "场馆",
          "运动场馆类地标，适合与体育馆形成区分样本。",
          "游泳馆位于体育馆北侧，是运动场馆类地标，后续可通过入口、外墙和周边环境与体育馆区分。",
          "体育馆北侧", 45.39, 41.25),
      landmark(8L, "L08", "第一饭堂", "The First Dining Hall", "生活服务",
          "生活服务类建筑，面向学生日常场景。",
          "第一饭堂位于尚学路西侧，属于学生高频到达地点，可支撑新生和访客的日常导览场景。",
          "尚学路西侧", 33.8, 47.17),
      landmark(9L, "L09", "第二饭堂", "The Second Dining Hall", "生活服务",
          "生活服务类建筑，可与第一饭堂对比识别。",
          "第二饭堂位于东二门附近，与第一饭堂同属生活服务类建筑，后续需要通过样本角度和位置特征提升区分度。",
          "东二门附近", 37.96, 21.84),
      landmark(10L, "L10", "中心酒店", "Hotel", "建筑",
          "校内接待建筑，靠近北门且地图标注清晰。",
          "中心酒店位于北门内侧，主要用于校内接待和住宿服务，适合纳入访客导览场景。",
          "北门内侧", 62.28, 10.12));

  public List<LandmarkSummary> list() {
    return landmarks.stream().map(this::summary).toList();
  }

  public Optional<LandmarkDetail> findById(Long id) {
    return landmarks.stream().filter(item -> item.id().equals(id)).findFirst();
  }

  public Optional<LandmarkDetail> findByCode(String code) {
    return landmarks.stream().filter(item -> item.code().equalsIgnoreCase(code)).findFirst();
  }

  public List<LandmarkDetail> topCandidates() {
    return landmarks.stream()
        .sorted(Comparator.comparing(LandmarkDetail::code))
        .limit(5)
        .toList();
  }

  private LandmarkSummary summary(LandmarkDetail detail) {
    return new LandmarkSummary(
        detail.id(),
        detail.code(),
        detail.name(),
        detail.englishName(),
        detail.type(),
        detail.summary(),
        detail.coverImageUrl(),
        detail.locationText(),
        detail.mapX(),
        detail.mapY());
  }

  private LandmarkDetail landmark(
      Long id,
      String code,
      String name,
      String englishName,
      String type,
      String summary,
      String description,
      String locationText,
      double mapX,
      double mapY) {
    String cover = "/images/landmarks/" + code.toLowerCase() + ".jpg";
    return new LandmarkDetail(
        id,
        code,
        name,
        englishName,
        type,
        summary,
        cover,
        description,
        locationText,
        mapX,
        mapY,
        List.of(new LandmarkImage(id * 100 + 1, cover, "front", "day", true)));
  }
}
