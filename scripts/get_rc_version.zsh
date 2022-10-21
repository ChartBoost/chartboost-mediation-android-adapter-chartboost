VERSION=`grep ' versionName ' ChartboostAdapter/build.gradle | sed 's/^[ \t]*versionName.*?: //g' | sed 's/"//g'`
RELEASE_CANDIDATE="$VERSION-rc"
# Fetch from private artifactory all the versions matching RELEASE_CANDIDATE
curl -sSf -u "$JFROG_USER:$JFROG_PASS" -O 'https://cboost.jfrog.io/artifactory/private-helium/com/chartboost/helium-adapter-chartboost/maven-metadata.xml'
# NEXT_VERSION gets the top rc and adds 1 to it. For example, 1.2.3.4.5-rc2 returns 3
NEXT_VERSION=`grep "$RELEASE_CANDIDATE" maven-metadata.xml | sed "s/      <version>$RELEASE_CANDIDATE\([0-9][0-9]*\)<\/version>/\1/g" | awk '{if ($0 > max) max = $0 } END {print max + 1}'`
# We print this out as output
echo "$RELEASE_CANDIDATE$NEXT_VERSION"
# Cleaning up that file we downloaded earlier
rm maven-metadata.xml
