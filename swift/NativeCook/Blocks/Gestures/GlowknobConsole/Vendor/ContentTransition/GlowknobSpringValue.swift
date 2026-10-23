// Vendored from expo-content-transition 0.1.7 (ios/Animation/GlowknobSpringValue.swift)
// https://github.com/rit3zh/expo-content-transition — MIT License, see THIRD_PARTY.md.
// Unmodified except where marked "Glowknob:".

import CoreGraphics
import Foundation

struct GlowknobSpringValue {
  var spring: GlowknobSpring
  private(set) var value: CGFloat
  private(set) var target: CGFloat
  private(set) var velocity: CGFloat = 0
  private let epsilon: CGFloat

  init(spring: GlowknobSpring, initial: CGFloat, epsilon: CGFloat) {
    self.spring = spring
    self.value = initial
    self.target = initial
    self.epsilon = epsilon
  }

  mutating func snap(to newValue: CGFloat) {
    value = newValue
    target = newValue
    velocity = 0
  }

  mutating func retarget(_ newTarget: CGFloat) {
    target = newTarget
  }

  mutating func reset(from: CGFloat, to: CGFloat) {
    value = from
    target = to
    velocity = 0
  }

  var isSettled: Bool {
    abs(value - target) < epsilon && abs(velocity) < epsilon * 8
  }

  mutating func tick(_ deltaTime: CGFloat) -> Bool {
    if isSettled {
      settle()
      return false
    }

    spring.advance(&self, deltaTime: deltaTime)
    if isSettled {
      settle()
      return false
    }

    return true
  }

  mutating func write(value newValue: CGFloat, velocity newVelocity: CGFloat) {
    value = newValue
    velocity = newVelocity
  }

  private mutating func settle() {
    value = target
    velocity = 0
  }
}
