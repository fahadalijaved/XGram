/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// XGram: vendored from material-foundation/material-color-utilities (java/, commit bd811e4c of 2026-10-19) under
// the Apache License 2.0 above, for the Expressive colour source (XgramColorSource). Changes: the package moved
// under com.fahadali.xgram.appearance.mcu, and the Error Prone annotations (@Var, @CanIgnoreReturnValue) were removed.

package com.fahadali.xgram.appearance.mcu.scheme;

import com.fahadali.xgram.appearance.mcu.dynamiccolor.ColorSpec.SpecVersion;
import com.fahadali.xgram.appearance.mcu.dynamiccolor.ColorSpecs;
import com.fahadali.xgram.appearance.mcu.dynamiccolor.DynamicScheme;
import com.fahadali.xgram.appearance.mcu.dynamiccolor.Variant;
import com.fahadali.xgram.appearance.mcu.hct.Hct;
import java.util.Collections;
import java.util.List;

/** A playful theme - the source color's hue does not appear in the theme. */
public class SchemeExpressive extends DynamicScheme {

  public SchemeExpressive(Hct sourceColorHct, boolean isDark, double contrastLevel) {
    this(sourceColorHct, isDark, contrastLevel, DEFAULT_SPEC_VERSION, DEFAULT_PLATFORM);
  }

  public SchemeExpressive(
      Hct sourceColorHct,
      boolean isDark,
      double contrastLevel,
      SpecVersion specVersion,
      Platform platform) {
    this(Collections.singletonList(sourceColorHct), isDark, contrastLevel, specVersion, platform);
  }

  public SchemeExpressive(List<Hct> sourceColorHctList, boolean isDark, double contrastLevel) {
    this(sourceColorHctList, isDark, contrastLevel, DEFAULT_SPEC_VERSION, DEFAULT_PLATFORM);
  }

  public SchemeExpressive(
      List<Hct> sourceColorHctList,
      boolean isDark,
      double contrastLevel,
      SpecVersion specVersion,
      Platform platform) {
    super(
        sourceColorHctList,
        Variant.EXPRESSIVE,
        isDark,
        contrastLevel,
        platform,
        specVersion,
        ColorSpecs.get(specVersion)
            .getPrimaryPalette(
                Variant.EXPRESSIVE, sourceColorHctList.get(0), isDark, platform, contrastLevel),
        ColorSpecs.get(specVersion)
            .getSecondaryPalette(
                Variant.EXPRESSIVE, sourceColorHctList.get(0), isDark, platform, contrastLevel),
        ColorSpecs.get(specVersion)
            .getTertiaryPalette(
                Variant.EXPRESSIVE, sourceColorHctList.get(0), isDark, platform, contrastLevel),
        ColorSpecs.get(specVersion)
            .getNeutralPalette(
                Variant.EXPRESSIVE, sourceColorHctList.get(0), isDark, platform, contrastLevel),
        ColorSpecs.get(specVersion)
            .getNeutralVariantPalette(
                Variant.EXPRESSIVE, sourceColorHctList.get(0), isDark, platform, contrastLevel),
        ColorSpecs.get(specVersion)
            .getErrorPalette(
                Variant.EXPRESSIVE, sourceColorHctList.get(0), isDark, platform, contrastLevel));
  }
}

