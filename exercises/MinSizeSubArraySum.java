public class MinSizeSubArraySum {
    public static void main(String[] args) {
        MinSizeSubArraySum solution = new MinSizeSubArraySum();

        int target1 = 7;
        int[] nums1 = {2, 3, 1, 2, 4, 3};
        System.out.println(solution.minSubArrayLen(target1, nums1)); // Output: 2

        int target2 = 4;
        int[] nums2 = {1, 4, 4};
        System.out.println(solution.minSubArrayLen(target2, nums2)); // Output: 1

        int target3 = 11;
        int[] nums3 = {1, 1, 1, 1, 1, 1, 1, 1};
        System.out.println(solution.minSubArrayLen(target3, nums3)); // Output: 0
    }
    public int minSubArrayLen(int target, int[] nums) {
        int left = 0;
        int sum = 0;
        int minLength = Integer.MAX_VALUE;

        for (int right = 0; right < nums.length; right++) {
            sum += nums[right];

            while (sum >= target) {
                minLength = Math.min(minLength, right - left + 1);
                sum -= nums[left];
                left++;
            }
        }

        return minLength == Integer.MAX_VALUE ? 0 : minLength;
    }
}