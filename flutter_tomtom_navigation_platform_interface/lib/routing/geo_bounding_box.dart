// Original page: https://developer.tomtom.com/assets/downloads/tomtom-sdks/android/api-reference/0.37.0/location/model/com.tomtom.sdk.location/-geo-bounding-box/index.html

import 'package:flutter_tomtom_navigation_platform_interface/routing/geo_point.dart';
import 'package:json_annotation/json_annotation.dart';

part 'geo_bounding_box.g.dart';

@JsonSerializable(explicitToJson: true)
class GeoBoundingBox {
  GeoBoundingBox({
    required this.topLeft,

    /// Default value GeoPoint,
    required this.bottomRight,

    /// Default value GeoPoint,
  }) : center = GeoPoint(
          latitude: (topLeft.latitude + bottomRight.latitude) / 2,
          longitude: (topLeft.longitude + bottomRight.longitude) / 2,
        );

  factory GeoBoundingBox.fromJson(Map<String, dynamic> json) =>
      _$GeoBoundingBoxFromJson(json);

  final GeoPoint bottomRight;
  @JsonKey(includeToJson: true, includeFromJson: true)
  final GeoPoint center;
  final GeoPoint topLeft;

  Map<String, dynamic> toJson() => _$GeoBoundingBoxToJson(this);
}
