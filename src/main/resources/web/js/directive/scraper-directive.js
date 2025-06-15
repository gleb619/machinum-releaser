export function initScraperDirective() {
    Alpine.directive('scraper', (el, { expression }, { evaluateLater, cleanup }) => {
        const getUrl = evaluateLater(expression);
        let iframe = null;

        const scrape = () => {
            getUrl(url => {
                if (!url) return;

                if (iframe) {
                    document.body.removeChild(iframe);
                }

                iframe = document.createElement('iframe');
                iframe.style.cssText = 'visibility:hidden; display:none; width:0; height:0; border:none;';

                const handleLoad = () => {
                    try {
                        const doc = iframe.contentDocument || iframe.contentWindow.document;

                        const extract = () => {
                            console.info("------------");
                            try {
                                const selectors = {
                                    title: '.seriestitlenu, h1, .title',
                                    author: '#showauthors a, .author',
                                    rating: '.uvotes, .rating',
                                    status: '#editstatus, .status',
                                    description: '#editdescription p, .description',
                                    cover: '.seriesimg img, .cover img',
                                    genres: '#seriesgenre a, .genre',
                                    chapters: '#myTable tr:not(:first-child), .chapter-list tr'
                                };

                                const data = {
                                    url: url,
                                    scraped_at: new Date().toISOString()
                                };

                                const getText = (selector) => {
                                    const el = doc.querySelector(selector);
                                    return el ? el.textContent.trim() : null;
                                };

                                const getAttr = (selector, attr) => {
                                    const el = doc.querySelector(selector);
                                    return el ? el.getAttribute(attr) : null;
                                };

                                const getMultiText = (selector) => {
                                    const els = doc.querySelectorAll(selector);
                                    return Array.from(els).map(el => el.textContent.trim());
                                };

                                data.title = getText(selectors.title);
                                console.info("data: ", data);
                                data.author = getText(selectors.author);
                                data.status = getText(selectors.status);
                                data.description = getText(selectors.description);
                                data.cover_url = getAttr(selectors.cover, 'src');
                                data.genres = getMultiText(selectors.genres);

                                const ratingText = getText(selectors.rating);
                                data.rating = ratingText ? parseFloat(ratingText.replace(/[^\d.]/g, '')) : null;

                                const rows = doc.querySelectorAll(selectors.chapters);
                                data.chapters = Array.from(rows).slice(0, 20).map(row => {
                                    const cells = row.querySelectorAll('td');
                                    if (cells.length < 3) return null;

                                    const titleLink = cells[0].querySelector('a');
                                    return {
                                        title: titleLink ? titleLink.textContent.trim() : null,
                                        url: titleLink ? titleLink.href : null,
                                        group: cells[1] ? cells[1].textContent.trim() : null,
                                        date: cells[2] ? cells[2].textContent.trim() : null
                                    };
                                }).filter(ch => ch && ch.title);

                                el.dispatchEvent(new CustomEvent('scraper-result', {
                                    detail: data,
                                    bubbles: true
                                }));
                            } catch (e) {
                                el.dispatchEvent(new CustomEvent('scraper-result', {
                                    detail: { error: `Extraction failed: ${e.message}` },
                                    bubbles: true
                                }));
                            }

                            document.body.removeChild(iframe);
                            iframe = null;
                        };

                        if (doc.readyState === 'complete') {
                            extract();
                        } else {
                            doc.addEventListener('DOMContentLoaded', extract);
                        }
                    } catch (e) {
                        console.error(`Scrape error: ${e.message}`, e);
                        el.dispatchEvent(new CustomEvent('scraper-result', {
                            detail: { error: 'Cross-origin access denied' },
                            bubbles: true
                        }));

                        if (iframe && iframe.parentNode) {
                            document.body.removeChild(iframe);
                        }
                        iframe = null;
                    }
                };

                iframe.addEventListener('load', handleLoad);
                document.body.appendChild(iframe);
                iframe.src = url;
            });
        };

        el.addEventListener('click', scrape);

        cleanup(() => {
            if (iframe && iframe.parentNode) {
                document.body.removeChild(iframe);
            }
        });
    });
}