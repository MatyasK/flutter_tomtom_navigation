// Original page: https://developer.tomtom.com/assets/downloads/tomtom-sdks/android/api-reference/0.37.0/navigation/navigation-engines/com.tomtom.sdk.navigation.progress/-route-stops-progress/index.html

import 'package:flutter_tomtom_navigation_platform_interface/json_helpers.dart';
import 'package:flutter_tomtom_navigation_platform_interface/quantity/distance.dart';
import 'package:json_annotation/json_annotation.dart';

part 'route_stops_progress.g.dart';

@JsonSerializable(explicitToJson: true)
class RouteStopsProgress {
  RouteStopsProgress({
    required this.routeStopId,
    this.remainingTime = Duration.zero,
    this.remainingDistance = Distance.zero,
    this.remainingTrafficDelay = Duration.zero,
  });

  factory RouteStopsProgress.fromJson(Map<String, dynamic> json) =>
      _$RouteStopsProgressFromJson(json);

  @JsonKey(fromJson: distanceFromRawValue)
  final Distance remainingDistance;
  @JsonKey(
    name: 'remainingTime',
    fromJson: durationFromHalfNanoseconds,
    toJson: durationToHalfNanoseconds,
  )
  final Duration remainingTime;
  @JsonKey(
    name: 'remainingTrafficDelay',
    fromJson: durationFromHalfNanoseconds,
    toJson: durationToHalfNanoseconds,
  )
  final Duration remainingTrafficDelay;
  final String routeStopId;

  Map<String, dynamic> toJson() => _$RouteStopsProgressToJson(this);
}
