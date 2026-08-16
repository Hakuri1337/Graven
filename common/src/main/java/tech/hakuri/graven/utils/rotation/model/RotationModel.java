package tech.hakuri.graven.utils.rotation.model;

import tech.hakuri.graven.utils.rotation.Rot2f;

@FunctionalInterface
public interface RotationModel {

    Rot2f tick(Rot2f from, Rot2f to, float timeDelta);
}
