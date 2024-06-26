// Original page: https://developer.tomtom.com/assets/downloads/tomtom-sdks/android/api-reference/0.37.0/location/model/com.tomtom.sdk.location/-geo-point/index.html

import 'package:json_annotation/json_annotation.dart';

part 'geo_point.g.dart';

@JsonSerializable(explicitToJson: true)
class GeoPoint {
  const GeoPoint({
    required this.latitude,

    /// Default value Double,
    required this.longitude,

    /// Default value Double,
  });

  factory GeoPoint.fromJson(Map<String, dynamic> json) =>
      _$GeoPointFromJson(json);

  final double latitude;
  final double longitude;

  Map<String, dynamic> toJson() => _$GeoPointToJson(this);

  static const currentLocation = GeoPoint(latitude: 0, longitude: 0);
}
