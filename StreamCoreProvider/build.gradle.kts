// Use an integer for version numbers
version = 3

cloudstream {
    description = "TMDB Media Provider"
    authors = listOf("sadhasivam")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     **/
    status = 1

    tvTypes = listOf("Movie", "TvSeries")

    requiresResources = false
    language = "en"

    iconUrl = "https://www.themoviedb.org/assets/2/v4/logos/v2/blue_square_2-d537fb228cf3ded904ef09b136fe3fec72548ebc1fea3fbbd1ad9e36364db38b.png"
}
