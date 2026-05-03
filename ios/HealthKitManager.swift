import Foundation
import HealthKit

// MARK: - HealthKitManager

final class HealthKitManager {

    static let shared = HealthKitManager()
    private let store = HKHealthStore()
    private let iso: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private init() {}

    // MARK: - Availability

    func isAvailable() -> Bool {
        return HKHealthStore.isHealthDataAvailable()
    }

    // MARK: - Authorization

    func requestAuthorization(
        types: [String],
        completion: @escaping (Bool, Error?) -> Void
    ) {
        guard isAvailable() else {
            completion(false, nil)
            return
        }
        let readTypes = buildObjectTypes(from: types)
        store.requestAuthorization(toShare: nil, read: readTypes) { success, error in
            completion(success, error)
        }
    }

    // MARK: - Read Samples

    func readSamples(
        types: [String],
        startDate: Date,
        endDate: Date
    ) async -> SyncData {
        guard isAvailable() else { return SyncData() }

        var result = SyncData()

        for typeString in types {
            let partial = await readType(typeString: typeString, startDate: startDate, endDate: endDate)
            result.merge(partial)
        }
        return result
    }

    // MARK: - Per-type reading

    private func readType(typeString: String, startDate: Date, endDate: Date) async -> SyncData {
        var data = SyncData()

        switch normalizedType(typeString) {

        // Quantity types
        case "steps":
            data.records += await readQuantity(identifier: .stepCount, unit: .count(), typeString: "HKQuantityTypeIdentifierStepCount", start: startDate, end: endDate)
        case "heartRate":
            data.records += await readQuantity(identifier: .heartRate, unit: HKUnit.count().unitDivided(by: .minute()), typeString: "HKQuantityTypeIdentifierHeartRate", start: startDate, end: endDate)
        case "restingHeartRate":
            data.records += await readQuantity(identifier: .restingHeartRate, unit: HKUnit.count().unitDivided(by: .minute()), typeString: "HKQuantityTypeIdentifierRestingHeartRate", start: startDate, end: endDate)
        case "heartRateVariabilitySDNN":
            data.records += await readQuantity(identifier: .heartRateVariabilitySDNN, unit: .secondUnit(with: .milli), typeString: "HKQuantityTypeIdentifierHeartRateVariabilitySDNN", start: startDate, end: endDate)
        case "oxygenSaturation", "bloodOxygen":
            data.records += await readQuantity(identifier: .oxygenSaturation, unit: .percent(), typeString: "HKQuantityTypeIdentifierOxygenSaturation", start: startDate, end: endDate)
        case "activeEnergy":
            data.records += await readQuantity(identifier: .activeEnergyBurned, unit: .kilocalorie(), typeString: "HKQuantityTypeIdentifierActiveEnergyBurned", start: startDate, end: endDate)
        case "basalEnergy", "restingEnergy":
            data.records += await readQuantity(identifier: .basalEnergyBurned, unit: .kilocalorie(), typeString: "HKQuantityTypeIdentifierBasalEnergyBurned", start: startDate, end: endDate)
        case "distanceWalkingRunning":
            data.records += await readQuantity(identifier: .distanceWalkingRunning, unit: .meter(), typeString: "HKQuantityTypeIdentifierDistanceWalkingRunning", start: startDate, end: endDate)
        case "distanceCycling":
            data.records += await readQuantity(identifier: .distanceCycling, unit: .meter(), typeString: "HKQuantityTypeIdentifierDistanceCycling", start: startDate, end: endDate)
        case "flightsClimbed":
            data.records += await readQuantity(identifier: .flightsClimbed, unit: .count(), typeString: "HKQuantityTypeIdentifierFlightsClimbed", start: startDate, end: endDate)
        case "bodyMass":
            data.records += await readQuantity(identifier: .bodyMass, unit: .gramUnit(with: .kilo), typeString: "HKQuantityTypeIdentifierBodyMass", start: startDate, end: endDate)
        case "height":
            data.records += await readQuantity(identifier: .height, unit: .meter(), typeString: "HKQuantityTypeIdentifierHeight", start: startDate, end: endDate)
        case "bodyFatPercentage":
            data.records += await readQuantity(identifier: .bodyFatPercentage, unit: .percent(), typeString: "HKQuantityTypeIdentifierBodyFatPercentage", start: startDate, end: endDate)
        case "bmi":
            data.records += await readQuantity(identifier: .bodyMassIndex, unit: .count(), typeString: "HKQuantityTypeIdentifierBodyMassIndex", start: startDate, end: endDate)
        case "leanBodyMass":
            data.records += await readQuantity(identifier: .leanBodyMass, unit: .gramUnit(with: .kilo), typeString: "HKQuantityTypeIdentifierLeanBodyMass", start: startDate, end: endDate)
        case "vo2Max":
            let vo2Unit = HKUnit.literUnit(with: .milli).unitDivided(by: HKUnit.gramUnit(with: .kilo).unitMultiplied(by: .minute()))
            data.records += await readQuantity(identifier: .vo2Max, unit: vo2Unit, typeString: "HKQuantityTypeIdentifierVO2Max", start: startDate, end: endDate)
        case "bloodGlucose":
            let glucoseUnit = HKUnit.moleUnit(with: .milli, molarMass: HKUnitMolarMassBloodGlucose)
            data.records += await readQuantity(identifier: .bloodGlucose, unit: glucoseUnit, typeString: "HKQuantityTypeIdentifierBloodGlucose", start: startDate, end: endDate)
        case "respiratoryRate":
            data.records += await readQuantity(identifier: .respiratoryRate, unit: HKUnit.count().unitDivided(by: .minute()), typeString: "HKQuantityTypeIdentifierRespiratoryRate", start: startDate, end: endDate)
        case "bodyTemperature":
            data.records += await readQuantity(identifier: .bodyTemperature, unit: .degreeCelsius(), typeString: "HKQuantityTypeIdentifierBodyTemperature", start: startDate, end: endDate)
        case "walkingSpeed":
            data.records += await readQuantity(identifier: .walkingSpeed, unit: HKUnit.meter().unitDivided(by: .second()), typeString: "HKQuantityTypeIdentifierWalkingSpeed", start: startDate, end: endDate)
        case "walkingStepLength":
            data.records += await readQuantity(identifier: .walkingStepLength, unit: .meter(), typeString: "HKQuantityTypeIdentifierWalkingStepLength", start: startDate, end: endDate)
        case "walkingAsymmetryPercentage":
            data.records += await readQuantity(identifier: .walkingAsymmetryPercentage, unit: .percent(), typeString: "HKQuantityTypeIdentifierWalkingAsymmetryPercentage", start: startDate, end: endDate)
        case "walkingDoubleSupportPercentage":
            data.records += await readQuantity(identifier: .walkingDoubleSupportPercentage, unit: .percent(), typeString: "HKQuantityTypeIdentifierWalkingDoubleSupportPercentage", start: startDate, end: endDate)
        case "sixMinuteWalkTestDistance":
            data.records += await readQuantity(identifier: .sixMinuteWalkTestDistance, unit: .meter(), typeString: "HKQuantityTypeIdentifierSixMinuteWalkTestDistance", start: startDate, end: endDate)
        case "waistCircumference":
            data.records += await readQuantity(identifier: .waistCircumference, unit: .meter(), typeString: "HKQuantityTypeIdentifierWaistCircumference", start: startDate, end: endDate)
        case "dietaryEnergyConsumed":
            data.records += await readQuantity(identifier: .dietaryEnergyConsumed, unit: .kilocalorie(), typeString: "HKQuantityTypeIdentifierDietaryEnergyConsumed", start: startDate, end: endDate)
        case "dietaryCarbohydrates":
            data.records += await readQuantity(identifier: .dietaryCarbohydrates, unit: .gram(), typeString: "HKQuantityTypeIdentifierDietaryCarbohydrates", start: startDate, end: endDate)
        case "dietaryProtein":
            data.records += await readQuantity(identifier: .dietaryProtein, unit: .gram(), typeString: "HKQuantityTypeIdentifierDietaryProtein", start: startDate, end: endDate)
        case "dietaryFatTotal":
            data.records += await readQuantity(identifier: .dietaryFatTotal, unit: .gram(), typeString: "HKQuantityTypeIdentifierDietaryFatTotal", start: startDate, end: endDate)
        case "dietaryWater":
            data.records += await readQuantity(identifier: .dietaryWater, unit: .liter(), typeString: "HKQuantityTypeIdentifierDietaryWater", start: startDate, end: endDate)

        // Blood Pressure (correlation)
        case "bloodPressureSystolic", "bloodPressureDiastolic", "bloodPressure":
            let bpRecords = await readBloodPressure(start: startDate, end: endDate)
            data.records += bpRecords

        // Sleep
        case "sleep":
            data.sleep += await readSleep(start: startDate, end: endDate)

        // Workout
        case "workout":
            data.workouts += await readWorkouts(start: startDate, end: endDate)

        default:
            break
        }

        return data
    }

    // MARK: - Quantity Reading

    private func readQuantity(
        identifier: HKQuantityTypeIdentifier,
        unit: HKUnit,
        typeString: String,
        start: Date,
        end: Date
    ) async -> [MetricRecord] {
        guard let quantityType = HKQuantityType.quantityType(forIdentifier: identifier) else {
            return []
        }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)

        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(
                sampleType: quantityType,
                predicate: predicate,
                limit: HKObjectQueryNoLimit,
                sortDescriptors: [sortDescriptor]
            ) { [weak self] _, samples, _ in
                guard let self = self else {
                    continuation.resume(returning: [])
                    return
                }
                let records = (samples as? [HKQuantitySample] ?? []).map { sample in
                    self.makeMetricRecord(sample: sample, typeString: typeString, unit: unit)
                }
                continuation.resume(returning: records)
            }
            store.execute(query)
        }
    }

    private func makeMetricRecord(sample: HKQuantitySample, typeString: String, unit: HKUnit) -> MetricRecord {
        let value = sample.quantity.doubleValue(for: unit)
        let unitString = unit.unitString
        return MetricRecord(
            id: sample.uuid.uuidString,
            parentId: nil,
            type: typeString,
            startDate: iso.string(from: sample.startDate),
            endDate: iso.string(from: sample.endDate),
            value: value,
            unit: unitString,
            zoneOffset: zoneOffsetString(),
            source: makeSourceInfo(from: sample)
        )
    }

    // MARK: - Blood Pressure

    private func readBloodPressure(start: Date, end: Date) async -> [MetricRecord] {
        guard let correlationType = HKCorrelationType.correlationType(forIdentifier: .bloodPressure),
              let systolicType = HKQuantityType.quantityType(forIdentifier: .bloodPressureSystolic),
              let diastolicType = HKQuantityType.quantityType(forIdentifier: .bloodPressureDiastolic) else {
            return []
        }

        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)

        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(
                sampleType: correlationType,
                predicate: predicate,
                limit: HKObjectQueryNoLimit,
                sortDescriptors: [sortDescriptor]
            ) { [weak self] _, samples, _ in
                guard let self = self else {
                    continuation.resume(returning: [])
                    return
                }
                var records: [MetricRecord] = []
                for sample in samples ?? [] {
                    guard let correlation = sample as? HKCorrelation else { continue }
                    let parentId = correlation.uuid.uuidString

                    if let systolicSample = correlation.objects(for: systolicType).first as? HKQuantitySample {
                        let value = systolicSample.quantity.doubleValue(for: .millimeterOfMercury())
                        records.append(MetricRecord(
                            id: systolicSample.uuid.uuidString,
                            parentId: parentId,
                            type: "HKQuantityTypeIdentifierBloodPressureSystolic",
                            startDate: self.iso.string(from: systolicSample.startDate),
                            endDate: self.iso.string(from: systolicSample.endDate),
                            value: value,
                            unit: HKUnit.millimeterOfMercury().unitString,
                            zoneOffset: self.zoneOffsetString(),
                            source: self.makeSourceInfo(from: correlation)
                        ))
                    }
                    if let diastolicSample = correlation.objects(for: diastolicType).first as? HKQuantitySample {
                        let value = diastolicSample.quantity.doubleValue(for: .millimeterOfMercury())
                        records.append(MetricRecord(
                            id: diastolicSample.uuid.uuidString,
                            parentId: parentId,
                            type: "HKQuantityTypeIdentifierBloodPressureDiastolic",
                            startDate: self.iso.string(from: diastolicSample.startDate),
                            endDate: self.iso.string(from: diastolicSample.endDate),
                            value: value,
                            unit: HKUnit.millimeterOfMercury().unitString,
                            zoneOffset: self.zoneOffsetString(),
                            source: self.makeSourceInfo(from: correlation)
                        ))
                    }
                }
                continuation.resume(returning: records)
            }
            store.execute(query)
        }
    }

    // MARK: - Sleep

    private func readSleep(start: Date, end: Date) async -> [SleepRecord] {
        guard let sleepType = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) else {
            return []
        }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)

        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(
                sampleType: sleepType,
                predicate: predicate,
                limit: HKObjectQueryNoLimit,
                sortDescriptors: [sortDescriptor]
            ) { [weak self] _, samples, _ in
                guard let self = self else {
                    continuation.resume(returning: [])
                    return
                }
                let records = (samples as? [HKCategorySample] ?? []).map { sample in
                    self.makeSleepRecord(sample: sample)
                }
                continuation.resume(returning: records)
            }
            store.execute(query)
        }
    }

    private func makeSleepRecord(sample: HKCategorySample) -> SleepRecord {
        let stage = sleepStage(from: sample.value)
        return SleepRecord(
            id: sample.uuid.uuidString,
            parentId: nil,
            stage: stage,
            startDate: iso.string(from: sample.startDate),
            endDate: iso.string(from: sample.endDate),
            zoneOffset: zoneOffsetString(),
            source: makeSourceInfo(from: sample),
            values: nil
        )
    }

    private func sleepStage(from value: Int) -> String {
        guard let sleepValue = HKCategoryValueSleepAnalysis(rawValue: value) else {
            return "unknown"
        }
        switch sleepValue {
        case .inBed:
            return "in_bed"
        case .asleepUnspecified:
            return "light"
        case .awake:
            return "awake"
        case .asleepCore:
            return "light"
        case .asleepDeep:
            return "deep"
        case .asleepREM:
            return "rem"
        @unknown default:
            return "unknown"
        }
    }

    // MARK: - Workouts

    private func readWorkouts(start: Date, end: Date) async -> [WorkoutRecord] {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)

        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(
                sampleType: HKWorkoutType.workoutType(),
                predicate: predicate,
                limit: HKObjectQueryNoLimit,
                sortDescriptors: [sortDescriptor]
            ) { [weak self] _, samples, _ in
                guard let self = self else {
                    continuation.resume(returning: [])
                    return
                }
                let records = (samples as? [HKWorkout] ?? []).map { workout in
                    self.makeWorkoutRecord(workout: workout)
                }
                continuation.resume(returning: records)
            }
            store.execute(query)
        }
    }

    private func makeWorkoutRecord(workout: HKWorkout) -> WorkoutRecord {
        var statistics: [WorkoutStatistic] = []

        if let energy = workout.totalEnergyBurned {
            let kcal = energy.doubleValue(for: .kilocalorie())
            statistics.append(WorkoutStatistic(type: "HKQuantityTypeIdentifierActiveEnergyBurned", unit: "kcal", value: kcal))
        }
        if let distance = workout.totalDistance {
            let meters = distance.doubleValue(for: .meter())
            statistics.append(WorkoutStatistic(type: "HKQuantityTypeIdentifierDistanceWalkingRunning", unit: "m", value: meters))
        }

        return WorkoutRecord(
            id: workout.uuid.uuidString,
            type: workoutActivityType(workout.workoutActivityType),
            startDate: iso.string(from: workout.startDate),
            endDate: iso.string(from: workout.endDate),
            zoneOffset: zoneOffsetString(),
            source: makeSourceInfo(from: workout),
            values: statistics,
            route: nil,
            samples: nil
        )
    }

    private func workoutActivityType(_ type: HKWorkoutActivityType) -> String {
        switch type {
        case .running: return "running"
        case .cycling: return "cycling"
        case .swimming: return "swimming"
        case .walking: return "walking"
        case .hiking: return "hiking"
        case .yoga: return "yoga"
        case .functionalStrengthTraining: return "strength_training"
        case .traditionalStrengthTraining: return "strength_training"
        case .crossTraining: return "cross_training"
        case .elliptical: return "elliptical"
        case .rowing: return "rowing"
        case .tennis: return "tennis"
        case .basketball: return "basketball"
        case .soccer: return "soccer"
        case .americanFootball: return "american_football"
        case .baseball: return "baseball"
        case .golf: return "golf"
        case .skiing: return "skiing"
        case .snowboarding: return "snowboarding"
        case .surfingSports: return "surfing"
        case .highIntensityIntervalTraining: return "hiit"
        case .pilates: return "pilates"
        case .dance: return "dance"
        case .jumpRope: return "jump_rope"
        case .stairClimbing: return "stair_climbing"
        case .mindAndBody: return "mind_and_body"
        case .mixedCardio: return "mixed_cardio"
        case .waterFitness: return "water_fitness"
        case .handball: return "handball"
        case .rugby: return "rugby"
        case .volleyball: return "volleyball"
        case .lacrosse: return "lacrosse"
        case .cricket: return "cricket"
        case .badminton: return "badminton"
        case .tableTennis: return "table_tennis"
        case .squash: return "squash"
        case .racquetball: return "racquetball"
        case .fencing: return "fencing"
        case .gymnastics: return "gymnastics"
        case .boxing: return "boxing"
        case .wrestling: return "wrestling"
        case .martialArts: return "martial_arts"
        case .skatingSports: return "skating"
        case .snowSports: return "snow_sports"
        case .waterPolo: return "water_polo"
        case .paddleSports: return "paddle_sports"
        case .sailing: return "sailing"
        case .archery: return "archery"
        case .fishing: return "fishing"
        case .hunting: return "hunting"
        case .preparationAndRecovery: return "recovery"
        case .other: return "other"
        default: return "other"
        }
    }

    // MARK: - HKObjectType set builder

    func buildObjectTypes(from typeStrings: [String]) -> Set<HKObjectType> {
        var types = Set<HKObjectType>()

        for typeString in typeStrings {
            switch normalizedType(typeString) {
            case "steps":
                addQuantityType(.stepCount, to: &types)
            case "heartRate":
                addQuantityType(.heartRate, to: &types)
            case "restingHeartRate":
                addQuantityType(.restingHeartRate, to: &types)
            case "heartRateVariabilitySDNN":
                addQuantityType(.heartRateVariabilitySDNN, to: &types)
            case "oxygenSaturation", "bloodOxygen":
                addQuantityType(.oxygenSaturation, to: &types)
            case "activeEnergy":
                addQuantityType(.activeEnergyBurned, to: &types)
            case "basalEnergy", "restingEnergy":
                addQuantityType(.basalEnergyBurned, to: &types)
            case "distanceWalkingRunning":
                addQuantityType(.distanceWalkingRunning, to: &types)
            case "distanceCycling":
                addQuantityType(.distanceCycling, to: &types)
            case "flightsClimbed":
                addQuantityType(.flightsClimbed, to: &types)
            case "bodyMass":
                addQuantityType(.bodyMass, to: &types)
            case "height":
                addQuantityType(.height, to: &types)
            case "bodyFatPercentage":
                addQuantityType(.bodyFatPercentage, to: &types)
            case "bmi":
                addQuantityType(.bodyMassIndex, to: &types)
            case "leanBodyMass":
                addQuantityType(.leanBodyMass, to: &types)
            case "vo2Max":
                addQuantityType(.vo2Max, to: &types)
            case "bloodGlucose":
                addQuantityType(.bloodGlucose, to: &types)
            case "respiratoryRate":
                addQuantityType(.respiratoryRate, to: &types)
            case "bodyTemperature":
                addQuantityType(.bodyTemperature, to: &types)
            case "walkingSpeed":
                addQuantityType(.walkingSpeed, to: &types)
            case "walkingStepLength":
                addQuantityType(.walkingStepLength, to: &types)
            case "walkingAsymmetryPercentage":
                addQuantityType(.walkingAsymmetryPercentage, to: &types)
            case "walkingDoubleSupportPercentage":
                addQuantityType(.walkingDoubleSupportPercentage, to: &types)
            case "sixMinuteWalkTestDistance":
                addQuantityType(.sixMinuteWalkTestDistance, to: &types)
            case "waistCircumference":
                addQuantityType(.waistCircumference, to: &types)
            case "dietaryEnergyConsumed":
                addQuantityType(.dietaryEnergyConsumed, to: &types)
            case "dietaryCarbohydrates":
                addQuantityType(.dietaryCarbohydrates, to: &types)
            case "dietaryProtein":
                addQuantityType(.dietaryProtein, to: &types)
            case "dietaryFatTotal":
                addQuantityType(.dietaryFatTotal, to: &types)
            case "dietaryWater":
                addQuantityType(.dietaryWater, to: &types)
            case "bloodPressureSystolic", "bloodPressureDiastolic", "bloodPressure":
                if let t = HKCorrelationType.correlationType(forIdentifier: .bloodPressure) { types.insert(t) }
                addQuantityType(.bloodPressureSystolic, to: &types)
                addQuantityType(.bloodPressureDiastolic, to: &types)
            case "sleep":
                if let t = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) { types.insert(t) }
            case "workout":
                types.insert(HKWorkoutType.workoutType())
            default:
                break
            }
        }
        return types
    }

    private func addQuantityType(_ identifier: HKQuantityTypeIdentifier, to set: inout Set<HKObjectType>) {
        if let t = HKQuantityType.quantityType(forIdentifier: identifier) { set.insert(t) }
    }

    // MARK: - Observer Queries for Background Delivery

    func enableBackgroundDelivery(for typeStrings: [String], handler: @escaping () -> Void) {
        guard isAvailable() else { return }
        let objectTypes = buildObjectTypes(from: typeStrings)
        for objectType in objectTypes {
            guard let sampleType = objectType as? HKSampleType else { continue }
            store.enableBackgroundDelivery(for: sampleType, frequency: .immediate) { _, _ in }
            let query = HKObserverQuery(sampleType: sampleType, predicate: nil) { _, completionHandler, _ in
                handler()
                completionHandler()
            }
            store.execute(query)
        }
    }

    // MARK: - Helpers

    private func normalizedType(_ typeString: String) -> String {
        // Strip HKQuantityTypeIdentifier prefix for convenience
        var s = typeString
        if s.hasPrefix("HKQuantityTypeIdentifier") {
            s = String(s.dropFirst("HKQuantityTypeIdentifier".count))
            // lower-case first char
            s = s.prefix(1).lowercased() + s.dropFirst()
        }
        if s.hasPrefix("HKCategoryTypeIdentifier") {
            s = String(s.dropFirst("HKCategoryTypeIdentifier".count))
            s = s.prefix(1).lowercased() + s.dropFirst()
        }
        // Canonical aliases
        switch s {
        case "stepCount": return "steps"
        case "activeEnergyBurned": return "activeEnergy"
        case "basalEnergyBurned": return "basalEnergy"
        case "sleepAnalysis": return "sleep"
        default: return s
        }
    }

    private func makeSourceInfo(from sample: HKSample) -> SourceInfo {
        let source = sample.sourceRevision.source
        let device = sample.device
        return SourceInfo(
            name: source.name,
            bundleIdentifier: source.bundleIdentifier,
            deviceModel: device?.model,
            deviceManufacturer: device?.manufacturer ?? "Apple",
            deviceType: deviceTypeString(device)
        )
    }

    private func deviceTypeString(_ device: HKDevice?) -> String? {
        guard let device = device else { return nil }
        let model = device.model?.lowercased() ?? ""
        if model.contains("watch") { return "watch" }
        if model.contains("iphone") { return "phone" }
        if model.contains("ipad") { return "tablet" }
        return "unknown"
    }

    private func zoneOffsetString() -> String {
        let tz = TimeZone.current
        let seconds = tz.secondsFromGMT()
        let hours = abs(seconds) / 3600
        let minutes = (abs(seconds) % 3600) / 60
        let sign = seconds >= 0 ? "+" : "-"
        return String(format: "%@%02d:%02d", sign, hours, minutes)
    }
}
