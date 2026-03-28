import java.util.HashMap;
import java.util.Map;

public class LongestSubstringKDistinct {
    public int lengthOfLongestSubstringKDistinct(String s, int k) {
        if(s.length()==0 || s == null || k == 0){
            return 0;
        }
        int left=0;
        int right=0;
        int maxLength=0;
        Map<Character, Integer> charSet = new HashMap<>();
        for(right=0;right<s.length();right++){
            Character ch = s.charAt(right);
            charSet.put(ch, charSet.getOrDefault(ch,0)+1);
            while(charSet.size()>k){
                Character leftChar=s.charAt(left);
                charSet.put(leftChar,charSet.get(leftChar)-1);
                if(charSet.get(leftChar)==0){
                    charSet.remove(leftChar);
                }
                left++;
            }
            maxLength=Math.max(maxLength,right-left+1);

        }
        return maxLength;
    }

    public static void main(String[] args) {
        LongestSubstringKDistinct solver = new LongestSubstringKDistinct();
        System.out.println(solver.lengthOfLongestSubstringKDistinct("eceba", 2)); // Output: 3
    }
}