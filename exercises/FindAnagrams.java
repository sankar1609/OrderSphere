public class FindAnagrams {
    public static List<Integer> findAnagrams(String s, String p){
        List<Integer> result = new ArrayList<>();
        if(s.length()<p.length()){
            return result;
        }
        int k=p.length();
        int[] sCount = int[26];
        int[] pCount = int[26];

        for(int i=0;i<k;i++){
            sCount[s.charAt(i)-'a']++;
            pCount[p.charAt(i)-'a']++;
        }
        if(sCount.equals(pCount)){
            result.add(0);
        }
        for(int i=k;i<s.length();i++){
            sCount[charAt(i)-'a']++;
            sCount[charAt(i-k)-'a']--;
            if(sCount.equals(pCount)){
                result.add(i-k+1);
            }
        }
        return result;

    }
}