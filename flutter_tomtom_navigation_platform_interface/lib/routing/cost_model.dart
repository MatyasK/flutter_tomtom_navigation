// Original page: https://developer.tomtom.com/assets/downloads/tomtom-sdks/android/api-reference/0.37.0/routing/model/com.tomtom.sdk.routing.options.calculation/-cost-model/index.html

import 'package:flutter_tomtom_navigation_platform_interface/routing/avoid_options.dart';
import 'package:flutter_tomtom_navigation_platform_interface/routing/consider_traffic.dart';
import 'package:flutter_tomtom_navigation_platform_interface/routing/route_type.dart';
import 'package:json_annotation/json_annotation.dart';

part 'cost_model.g.dart';

@JsonSerializable(explicitToJson: true)
class CostModel {
  const CostModel({
    this.routeType = RouteType.fast,

    /// Default value RouteType = RouteType.Fast,
    this.considerTraffic = ConsiderTraffic.yes,

    /// Default value ConsiderTraffic = ConsiderTraffic.Yes,
    this.avoidOptions,

    /// Default value AvoidOptions? = null,
  });

  factory CostModel.fromJson(Map<String, dynamic> json) =>
      _$CostModelFromJson(json);

  final AvoidOptions? avoidOptions;
  final ConsiderTraffic considerTraffic;
  final RouteType routeType;

  Map<String, dynamic> toJson() => _$CostModelToJson(this);
}
