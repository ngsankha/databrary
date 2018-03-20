{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Databrary.Model.Metric.TypesTest where

import Test.Tasty
import Test.Tasty.HUnit

import Databrary.Model.Metric.Types

participantFieldMapping1 :: ParticipantFieldMapping
participantFieldMapping1 =
    ParticipantFieldMapping
        { pfmId = Just "col1"
        , pfmInfo = Nothing
        , pfmDescription = Nothing
        , pfmBirthdate = Nothing
        , pfmGender = Just "col2"
        , pfmRace = Nothing
        , pfmEthnicity = Nothing
        , pfmGestationalAge = Nothing
        , pfmPregnancyTerm = Nothing
        , pfmBirthWeight = Nothing
        , pfmDisability = Nothing
        , pfmLanguage = Nothing
        , pfmCountry = Nothing
        , pfmState = Nothing
        , pfmSetting = Nothing
        }

participantFieldMappingAll :: ParticipantFieldMapping
participantFieldMappingAll =
    ParticipantFieldMapping
        { pfmId = Just "id"
        , pfmInfo = Just "info"
        , pfmDescription = Just "description"
        , pfmBirthdate = Just "birthdate"
        , pfmGender = Just "gender"
        , pfmRace = Just "race"
        , pfmEthnicity = Just "ethnicity"
        , pfmGestationalAge = Just "gestationalage"
        , pfmPregnancyTerm = Just "pregnancyterm"
        , pfmBirthWeight = Just "birthweight"
        , pfmDisability = Just "disability"
        , pfmLanguage = Just "language"
        , pfmCountry = Just "country"
        , pfmState = Just "state"
        , pfmSetting = Just "setting"
        }

emptyParticipantFieldMapping :: ParticipantFieldMapping
emptyParticipantFieldMapping =
    ParticipantFieldMapping
        { pfmId = Nothing
        , pfmInfo = Nothing
        , pfmDescription = Nothing
        , pfmBirthdate = Nothing
        , pfmGender = Nothing
        , pfmRace = Nothing
        , pfmEthnicity = Nothing
        , pfmGestationalAge = Nothing
        , pfmPregnancyTerm = Nothing
        , pfmBirthWeight = Nothing
        , pfmDisability = Nothing
        , pfmLanguage = Nothing
        , pfmCountry = Nothing
        , pfmState = Nothing
        , pfmSetting = Nothing
        }

tests :: TestTree
tests = testGroup "Databrary.Model.Metric.Types"
    [
    ]
