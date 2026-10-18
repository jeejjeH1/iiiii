// Tiny static file server (ES modules don't load from file://).
const http = require('http');
const fs = require('fs');
const path = require('path');

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.wav': 'audio/wav', '.mp4': 'video/mp4', '.css': 'text/css' };

function serve(root, port = 0) {
  return new Promise((resolve) => {
    const srv = http.createServer((req, res) => {
      const p = path.join(root, decodeURIComponent(req.url.split('?')[0]));
      if (!p.startsWith(root)) { res.writeHead(403); return res.end(); }
      fs.readFile(p, (err, buf) => {
        if (err) { res.writeHead(404); return res.end(); }
        res.writeHead(200, { 'Content-Type': TYPES[path.extname(p)] || 'application/octet-stream' });
        res.end(buf);
      });
    });
    srv.listen(port, '127.0.0.1', () => resolve({ url: `http://127.0.0.1:${srv.address().port}`, close: () => srv.close() }));
  });
}
module.exports = { serve };

if (require.main === module) {
  serve(path.resolve(process.argv[2] || path.join(__dirname, '..')), Number(process.argv[3] || 8080)).then(({ url }) => console.log(url));
}
