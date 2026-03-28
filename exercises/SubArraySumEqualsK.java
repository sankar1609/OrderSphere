public class SubArraySumEqualsK {
    public int subarraySum(int[] nums, int k) {
        int count = 0;
        int sum = 0;
        Map<Integer, Integer> map = new HashMap<>();
        map.put(0, 1); // Base case: there's one way to have a sum of 0 (using no elements)

        for (int num : nums) {
            sum += num; // Update the cumulative sum
            if (map.containsKey(sum - k)) {
                count += map.get(sum - k); // If there's a previous sum that matches, add its count
            }
            map.put(sum, map.getOrDefault(sum, 0) + 1); // Update the count of the current sum
        }

        return count;
    }
    public static void main(String[] args) {
        SubArraySumEqualsK solver = new SubArraySumEqualsK();
        int[] nums = {1, 1, 1};
        int k = 2;
        System.out.println(solver.subarraySum(nums, k)); // Output: 2
    }
}