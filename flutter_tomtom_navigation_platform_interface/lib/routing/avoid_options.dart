// Original page: https://developer.tomtom.com/assets/downloads/tomtom-sdks/android/api-reference/0.37.0/routing/model/com.tomtom.sdk.routing.options.calculation/-avoid-options/index.html

import 'package:flutter_tomtom_navigation_platform_interface/routing/avoid_type.dart';
import 'package:flutter_tomtom_navigation_platform_interface/routing/geo_bounding_box.dart';
import 'package:flutter_tomtom_navigation_platform_interface/routing/vignettes.dart';
import 'package:json_annotation/json_annotation.dart';

part 'avoid_options.g.dart';

@JsonSerializable(explicitToJson: true)
class AvoidOptions {
  const AvoidOptions({
    this.avoidTypes = const {},

    /// Default value Set<AvoidType> = emptySet(),
    this.avoidAreas = const {},

    /// Default value Vignettes? = null,
    this.vignettes,
  });

  factory AvoidOptions.fromJson(Map<String, dynamic> json) =>
      _$AvoidOptionsFromJson(json);

  final Set<GeoBoundingBox> avoidAreas;
  final Set<AvoidType> avoidTypes;
  final Vignettes? vignettes;

  Map<String, dynamic> toJson() => _$AvoidOptionsToJson(this);
}
