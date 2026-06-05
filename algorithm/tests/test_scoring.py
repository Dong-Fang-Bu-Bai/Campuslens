import unittest

from app.utils.scoring import mahalanobis_match_score, match_level


class ScoringTests(unittest.TestCase):
    def test_score_stays_in_unit_range(self):
        for distance in [0, 10, 900, 5000, 100000]:
            score = mahalanobis_match_score(distance)
            self.assertGreaterEqual(score, 0.0)
            self.assertLessEqual(score, 1.0)

    def test_score_decreases_as_distance_grows(self):
        near = mahalanobis_match_score(100)
        center = mahalanobis_match_score(900)
        far = mahalanobis_match_score(5000)

        self.assertGreater(near, center)
        self.assertGreater(center, far)

    def test_center_distance_maps_to_midpoint(self):
        self.assertAlmostEqual(mahalanobis_match_score(900), 0.5, places=6)

    def test_match_level_thresholds(self):
        self.assertEqual(match_level(0.80), "high")
        self.assertEqual(match_level(0.79), "medium")
        self.assertEqual(match_level(0.40), "medium")
        self.assertEqual(match_level(0.39), "low")

    def test_negative_distance_is_rejected(self):
        with self.assertRaises(ValueError):
            mahalanobis_match_score(-1)


if __name__ == "__main__":
    unittest.main()
